package com.example.truckfleetsystem.grpc;

import com.example.grpc.proto.*;
import com.example.truckfleetsystem.entity.RouteEntity;
import com.example.truckfleetsystem.entity.TruckEntity;
import com.example.truckfleetsystem.entity.TruckLoadEntity;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.springframework.grpc.server.service.GrpcService;
import com.example.truckfleetsystem.service.TruckLoadService;
import com.example.truckfleetsystem.service.TruckRouteService;
import com.example.truckfleetsystem.service.TruckService;

import java.util.List;
import java.util.Optional;

@GrpcService
public class TruckGRPCService extends TruckServiceGrpc.TruckServiceImplBase {

    private final TruckService truckService;
    private final TruckRouteService truckRouteService;
    private final TruckLoadService truckLoadService;

    public TruckGRPCService(
            TruckService truckService,
            TruckRouteService truckRouteService,
            TruckLoadService truckLoadService
    ) {
        this.truckService = truckService;
        this.truckRouteService = truckRouteService;
        this.truckLoadService = truckLoadService;
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

            TruckWithRoutes response = getTruckDetail(truckEntity);

            observer.onNext(response);
            observer.onCompleted();

        }
        catch (Exception e) {
            observer.onError(
                    Status.INTERNAL
                            .withDescription("error al obtener el camion")
                            .asRuntimeException()
            );
        }
    }

    @Override
    public void listTrucks(ListTrucksRequest request, StreamObserver<ListTrucksResponse> observer){
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

        }catch (Exception e){
            observer.onError(
                    Status.INTERNAL
                            .withDescription("error al obtener el detalle de los camiones")
                            .asRuntimeException()
            );
        }finally {
            System.out.println("67");
        }

    }

    @Override
    public void unloadTruck(UnloadTruckRequest request, StreamObserver<Truck> observer){
        try{
            Optional<TruckEntity> truckSaved = truckService.getTruck(request.getTruckId());
            Optional<TruckLoadEntity> truckLoadSaved = truckLoadService.findById(request.getLoadId());

            if (truckSaved.isEmpty()){
                observer.onError(
                        Status.NOT_FOUND
                                .withDescription("No se encontro el camion para descargar")
                                .asRuntimeException()
                );
                return;
            }
            if (truckLoadSaved.isEmpty()){
                observer.onError(
                        Status.NOT_FOUND
                                .withDescription("No se encontro la carga para descargar")
                                .asRuntimeException()
                );
                return;
            }

            TruckEntity truckChecked = truckSaved.get();
            TruckLoadEntity truckLoadChecked = truckLoadSaved.get();

            truckLoadService.unload(truckLoadChecked);

            List<TruckLoadEntity> loads = truckLoadService.findAllByTruck(truckChecked);
            observer.onNext(Truck
                    .newBuilder()
                    .setId(truckChecked.getId())
                    .setLicensePlate(truckChecked.getLicensePlate())
                    .setMaxCapacityKg(truckChecked.getMaxCapacityKg())
                    .addAllLoads(
                            loads.stream()
                                    .map(truckLoadEntity -> LoadItem.newBuilder()
                                            .setId(truckLoadEntity.getId())
                                            .setDetail(truckLoadEntity.getDetail())
                                            .setWeightKg(truckLoadEntity.getWeightKg())
                                            .build()
                                    )
                                    .toList()
                    )
                    .build()
            );
            observer.onCompleted();
        }catch (Exception e){
            observer.onError(
                    Status.INTERNAL
                            .withDescription("error al intentar descargar")
                            .asRuntimeException()
            );
        }
    }

    @Override
    public void loadTruck(LoadTruckRequest request, StreamObserver<Truck>observer) {
        try {
            Optional<TruckEntity> optionalTruck = truckService.getTruck(request.getTruckId());

            if (optionalTruck.isEmpty()) {
                observer.onError(
                        Status.NOT_FOUND
                                .withDescription("no se encontró el camión con id:" + request.getTruckId())
                                .asRuntimeException()
                );
                return;
            }

            TruckEntity truckEntity = optionalTruck.get();
            TruckEntity savedTruck = truckService.loadTruck(
                    truckEntity,
                    request.getDetail(),
                    request.getWeightKg()
            );
            List<TruckLoadEntity> loads = truckLoadService.findAllByTruck(savedTruck);

            observer.onNext(Truck
                    .newBuilder()
                    .setId(savedTruck.getId())
                    .setLicensePlate(savedTruck.getLicensePlate())
                    .setMaxCapacityKg(savedTruck.getMaxCapacityKg())
                    .addAllLoads(
                            loads.stream()
                                    .map(truckLoadEntity -> LoadItem.newBuilder()
                                            .setId(truckLoadEntity.getId())
                                            .setDetail(truckLoadEntity.getDetail())
                                            .setWeightKg(truckLoadEntity.getWeightKg())
                                            .build()
                                    )
                                    .toList()
                    )
                    .build()
            );

            observer.onCompleted();
        }catch (Exception e) {
            observer.onError(
                    Status.INTERNAL
                            .withDescription("error al intentar cargar")
                            .asRuntimeException()
            );
        }
    }

    private TruckWithRoutes getTruckDetail(TruckEntity truckEntity){

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
