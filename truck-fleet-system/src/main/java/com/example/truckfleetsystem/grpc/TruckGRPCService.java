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
            Optional<TruckEntity> optionalTruck = truckService.getTruck(request);

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