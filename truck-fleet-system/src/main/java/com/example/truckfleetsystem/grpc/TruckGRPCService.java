package com.example.truckfleetsystem.grpc;

import com.example.grpc.proto.*;
import com.example.truckfleetsystem.entity.RouteEntity;
import com.example.truckfleetsystem.entity.TruckEntity;
import com.example.truckfleetsystem.entity.TruckLoadEntity;
import com.example.truckfleetsystem.service.InsufficientCapacityException;
import com.example.truckfleetsystem.service.LoadNotFoundException;
import com.example.truckfleetsystem.service.LoadTruckMismatchException;
import com.example.truckfleetsystem.service.TruckNotFoundException;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.grpc.server.service.GrpcService;
import com.example.truckfleetsystem.service.TruckLoadService;
import com.example.truckfleetsystem.service.TruckRouteService;
import com.example.truckfleetsystem.service.TruckService;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

@GrpcService
public class TruckGRPCService extends TruckServiceGrpc.TruckServiceImplBase {

    private static final Logger log = LoggerFactory.getLogger(TruckGRPCService.class);

    private final TruckService truckService;
    private final TruckRouteService truckRouteService;
    private final TruckLoadService truckLoadService;

    private static final String CACHE_PREFIX = "truck:";
    static final Duration AVAILABILITY_CACHE_TTL = Duration.ofSeconds(60);
    private final RedisTemplate<String, byte[]> redisTemplate;

    public TruckGRPCService(
            TruckService truckService,
            TruckRouteService truckRouteService,
            TruckLoadService truckLoadService,
            RedisTemplate<String, byte[]> redisTemplate
    ) {
        this.truckService = truckService;
        this.truckRouteService = truckRouteService;
        this.truckLoadService = truckLoadService;
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void getTruck(
            GetTruckRequest request,
            StreamObserver<TruckWithRoutes> observer
    ) {
        try {
            Optional<TruckEntity> optionalTruck = truckService.getTruck(request.getTruckId());

            if (optionalTruck.isEmpty()) {
                observer.onError(
                        Status.NOT_FOUND
                                .withDescription("truck not found")
                                .asRuntimeException()
                );
                return;
            }
            TruckEntity truckEntity = optionalTruck.get();
            TruckWithRoutes responseSaved = getTruckDetail(truckEntity);

            observer.onNext(responseSaved);
            observer.onCompleted();

        } catch (Exception e) {
            observer.onError(
                    Status.INTERNAL
                            .withDescription("error al obtener el camion")
                            .asRuntimeException()
            );
        }
    }

    @Override
    public void listTrucks(ListTrucksRequest request, StreamObserver<ListTrucksResponse> observer) {
        try {
            List<TruckEntity> allTrucks = truckService.getAll();
            ListTrucksResponse response = ListTrucksResponse
                    .newBuilder()
                    .addAllTrucks(
                            allTrucks
                                    .stream()
                                    .map(this::getTruckDetail)
                                    .toList()
                    )
                    .build();

            observer.onNext(response);
            observer.onCompleted();

        } catch (Exception e) {
            observer.onError(
                    Status.INTERNAL
                            .withDescription("error al obtener el detalle de los camiones")
                            .asRuntimeException()
            );
        }

    }

    @Override
    public void unloadTruck(UnloadTruckRequest request, StreamObserver<Truck> observer) {
        try {
            TruckEntity truck = truckService.unloadTruck(request.getTruckId(), request.getLoadId());
            evictAvailability(truck.getId());

            observer.onNext(toTruckMessage(truck, truckLoadService.findAllByTruck(truck), null));
            observer.onCompleted();
        } catch (TruckNotFoundException | LoadNotFoundException e) {
            fail(observer, Status.NOT_FOUND, e.getMessage());
        } catch (LoadTruckMismatchException e) {
            fail(observer, Status.FAILED_PRECONDITION, e.getMessage());
        } catch (Exception e) {
            log.error("Error al descargar camión {} carga {}", request.getTruckId(), request.getLoadId(), e);
            fail(observer, Status.INTERNAL, "error al intentar descargar");
        }
    }

    @Override
    public void loadTruck(LoadTruckRequest request, StreamObserver<Truck> observer) {
        try {
            TruckLoadEntity created = truckService.loadTruck(
                    request.getTruckId(),
                    request.getDetail(),
                    request.getWeightKg()
            );
            TruckEntity truck = created.getTruck();
            evictAvailability(truck.getId());

            observer.onNext(toTruckMessage(truck, truckLoadService.findAllByTruck(truck), created));
            observer.onCompleted();
        } catch (TruckNotFoundException e) {
            fail(observer, Status.NOT_FOUND, e.getMessage());
        } catch (IllegalArgumentException e) {
            fail(observer, Status.INVALID_ARGUMENT, e.getMessage());
        } catch (InsufficientCapacityException e) {
            fail(observer, Status.FAILED_PRECONDITION, e.getMessage());
        } catch (Exception e) {
            log.error("Error al cargar camión {}", request.getTruckId(), e);
            fail(observer, Status.INTERNAL, "error al intentar cargar");
        }
    }

    @Override
    public void checkAvailability(GetTruckRequest request, StreamObserver<TruckAvailability> observer) {
        try {
            String key = availabilityCacheKey(request.getTruckId());
            TruckAvailability cached = readCachedAvailability(key);

            if (cached != null) {
                observer.onNext(cached);
                observer.onCompleted();
                return;
            }

            Optional<TruckEntity> truckSaved = truckService.getTruck(request.getTruckId());
            if (truckSaved.isEmpty()) {
                observer.onError(
                        Status.NOT_FOUND
                                .withDescription("no se encontró el camión con id:" + request.getTruckId())
                                .asRuntimeException()
                );
                return;
            }

            int availability = truckLoadService.calculateTruckAvailability(truckSaved.get());
            TruckAvailability response = TruckAvailability.newBuilder()
                    .setTruckId(truckSaved.get().getId())
                    .setMaxCapacityKg(truckSaved.get().getMaxCapacityKg())
                    .setAvailableCapacityKg(availability)
                    .build();

            writeCachedAvailability(key, response);

            observer.onNext(response);
            observer.onCompleted();

        } catch (Exception e) {
            observer.onError(
                    Status.INTERNAL
                            .withDescription("Error al devolver la disponibilidad")
                            .withCause(e)
                            .asRuntimeException()
            );
        }
    }

    private void fail(StreamObserver<?> observer, Status status, String description) {
        observer.onError(status.withDescription(description).asRuntimeException());
    }

    private String availabilityCacheKey(int truckId) {
        return CACHE_PREFIX + truckId;
    }

    private TruckAvailability readCachedAvailability(String key) {
        try {
            byte[] bytes = redisTemplate.opsForValue().get(key);
            return bytes == null ? null : TruckAvailability.parseFrom(bytes);
        } catch (Exception e) {
            log.warn("Caché de disponibilidad no disponible al leer {}: {}", key, e.toString());
            return null;
        }
    }

    private void writeCachedAvailability(String key, TruckAvailability value) {
        try {
            redisTemplate.opsForValue().set(key, value.toByteArray(), AVAILABILITY_CACHE_TTL);
        } catch (Exception e) {
            log.warn("Caché de disponibilidad no disponible al escribir {}: {}", key, e.toString());
        }
    }

    private void evictAvailability(int truckId) {
        try {
            redisTemplate.delete(availabilityCacheKey(truckId));
        } catch (Exception e) {
            log.warn("No se pudo invalidar la caché del camión {}: {}", truckId, e.toString());
        }
    }

    private LoadItem toLoadItem(TruckLoadEntity load) {
        return LoadItem.newBuilder()
                .setId(load.getId())
                .setWeightKg(load.getWeightKg())
                .setDetail(load.getDetail())
                .build();
    }

    private Truck toTruckMessage(TruckEntity truck, List<TruckLoadEntity> loads, TruckLoadEntity created) {
        Truck.Builder builder = Truck.newBuilder()
                .setId(truck.getId())
                .setLicensePlate(truck.getLicensePlate())
                .setMaxCapacityKg(truck.getMaxCapacityKg())
                .addAllLoads(loads.stream().map(this::toLoadItem).toList());
        if (created != null) {
            builder.setCreatedLoad(toLoadItem(created));
        }
        return builder.build();
    }

    private TruckWithRoutes getTruckDetail(TruckEntity truckEntity) {

        List<RouteEntity> routes = truckRouteService.findByTruckId(truckEntity.getId());

        List<TruckLoadEntity> loads = truckLoadService.findAllByTruck(truckEntity);

        List<LoadItem> protoLoads = loads.stream()
                .map(load -> LoadItem.newBuilder()
                        .setId(load.getId())
                        .setWeightKg(load.getWeightKg())
                        .setDetail(load.getDetail())
                        .build()
                )
                .toList();

        Truck protoTruck = Truck.newBuilder()
                .setId(truckEntity.getId())
                .setLicensePlate(truckEntity.getLicensePlate())
                .setMaxCapacityKg(truckEntity.getMaxCapacityKg())
                .addAllLoads(protoLoads)
                .build();

        List<Route> protoRoutes = routes
                .stream()
                .map(route ->
                        Route.newBuilder()
                                .setId(route.getId())
                                .setName(route.getName())
                                .setDistanceKm(route.getDistanceKm())
                                .setOrigin(route.getOrigin())
                                .setDestination(route.getDestination())
                                .build()
                )
                .toList();

        return TruckWithRoutes.newBuilder()
                .setTruck(protoTruck)
                .addAllRoutes(protoRoutes)
                .build();
    }
}
