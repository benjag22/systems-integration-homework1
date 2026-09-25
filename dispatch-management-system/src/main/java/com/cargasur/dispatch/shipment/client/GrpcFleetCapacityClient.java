package com.cargasur.dispatch.shipment.client;

import com.example.grpc.proto.*;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Cliente gRPC real hacia el servicio de Flota. Modo de invocación: unary
 * bloqueante con deadline corto, porque Despachos necesita la respuesta antes
 * de decidir si registra el pedido (ver ADR-002).
 */
@Component
@RequiredArgsConstructor
public class GrpcFleetCapacityClient implements FleetCapacityClient {

    private static final long DEADLINE_SECONDS = 2;

    private final TruckServiceGrpc.TruckServiceBlockingStub truckServiceBlockingStub;

    @Override
    public FleetReservationResult verifyAndReserveCapacity(Integer truckId, Integer weightKg) {
        try {
            Truck truck = stub().loadTruck(LoadTruckRequest.newBuilder()
                    .setTruckId(truckId)
                    .setWeightKg(weightKg)
                    .setDetail("Reserva por despacho")
                    .build());

            int loadId = truck.getLoadsList().stream()
                    .mapToInt(LoadItem::getId)
                    .max()
                    .orElseThrow(() -> new IllegalStateException("Flota no devolvió la carga reservada"));

            return new FleetReservationResult(true, loadId, "Capacidad confirmada en Flota");
        } catch (StatusRuntimeException e) {
            if (e.getStatus().getCode() == Status.Code.FAILED_PRECONDITION
                    || e.getStatus().getCode() == Status.Code.NOT_FOUND
                    || e.getStatus().getCode() == Status.Code.INVALID_ARGUMENT) {
                return new FleetReservationResult(false, null, describeFailure(e));
            }
            throw unavailable(e);
        }
    }

    @Override
    public void releaseCapacity(Integer truckId, Integer loadId, Integer weightKg) {
        try {
            stub().unloadTruck(UnloadTruckRequest.newBuilder()
                    .setTruckId(truckId)
                    .setLoadId(loadId)
                    .build());
        } catch (StatusRuntimeException e) {
            if (e.getStatus().getCode() == Status.Code.NOT_FOUND) {
                return; // ya estaba liberada / no existe: revertir es idempotente
            }
            throw unavailable(e);
        }
    }

    private TruckServiceGrpc.TruckServiceBlockingStub stub() {
        return truckServiceBlockingStub.withDeadlineAfter(DEADLINE_SECONDS, TimeUnit.SECONDS);
    }

    private String describeFailure(StatusRuntimeException e) {
        return "Flota rechazó la operación: " + e.getStatus().getDescription();
    }

    private FleetServiceUnavailableException unavailable(StatusRuntimeException e) {
        return new FleetServiceUnavailableException(
                "El servicio de Flota no respondió a tiempo: " + e.getStatus().getCode(), e);
    }
}
