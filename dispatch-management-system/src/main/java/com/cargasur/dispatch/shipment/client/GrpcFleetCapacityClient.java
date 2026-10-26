package com.cargasur.dispatch.shipment.client;

import com.cargasur.dispatch.config.exception.ResourceNotFoundException;
import com.example.grpc.proto.*;
import io.grpc.ManagedChannel;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Cliente gRPC real hacia el servicio de Flota. Modo de invocación: unario
 * bloqueante con deadline corto, porque Despachos necesita la respuesta antes
 * de decidir si registra el pedido (ver ADR-004 y ADR-008).
 *
 * Traducción de errores (ADR-004):
 *  - FAILED_PRECONDITION / NOT_FOUND / INVALID_ARGUMENT: Flota respondió y rechazó -> conflicto de negocio (409)
 *  - UNAVAILABLE / DEADLINE_EXCEEDED / RESOURCE_EXHAUSTED: infraestructura, reintentable -> 503 + Retry-After
 *  - cualquier otro código: error inesperado de Flota -> 502
 */
@Component
@RequiredArgsConstructor
public class GrpcFleetCapacityClient implements FleetCapacityClient {

    private static final long DEADLINE_SECONDS = 2;
    private static final long RECONNECT_NUDGE_INTERVAL_MS = 1000;

    private final TruckServiceGrpc.TruckServiceBlockingStub truckServiceBlockingStub;
    private final ManagedChannel channel;
    private final AtomicLong lastReconnectNudge = new AtomicLong(0);

    @Override
    public FleetReservationResult verifyAndReserveCapacity(Integer truckId, Integer weightKg) {
        try {
            Truck truck = stub().loadTruck(LoadTruckRequest.newBuilder()
                    .setTruckId(truckId)
                    .setWeightKg(weightKg)
                    .setDetail("Reserva por despacho")
                    .build());

            if (!truck.hasCreatedLoad()) {
                throw new FleetServiceException(
                        "Flota no informó la carga creada (contrato trucks.proto incompatible)", "INTERNAL", null);
            }

            return new FleetReservationResult(true, truck.getCreatedLoad().getId(), "Capacidad confirmada en Flota");
        } catch (StatusRuntimeException e) {
            Status.Code code = e.getStatus().getCode();
            if (code == Status.Code.FAILED_PRECONDITION
                    || code == Status.Code.NOT_FOUND
                    || code == Status.Code.INVALID_ARGUMENT) {
                return new FleetReservationResult(false, null, describeFailure(e));
            }
            throw translate(e);
        }
    }

    @Override
    public void releaseCapacity(Integer truckId, Integer loadId, Integer weightKg) {
        if (loadId == null) {
            throw new IllegalStateException("El despacho no tiene una carga asociada en Flota");
        }
        try {
            stub().unloadTruck(UnloadTruckRequest.newBuilder()
                    .setTruckId(truckId)
                    .setLoadId(loadId)
                    .build());
        } catch (StatusRuntimeException e) {
            Status.Code code = e.getStatus().getCode();
            if (code == Status.Code.NOT_FOUND) {
                return; // ya estaba liberada / no existe: revertir es idempotente
            }
            if (code == Status.Code.FAILED_PRECONDITION || code == Status.Code.INVALID_ARGUMENT) {
                throw new IllegalStateException(describeFailure(e));
            }
            throw translate(e);
        }
    }

    @Override
    public FleetAvailability checkAvailability(Integer truckId) {
        try {
            TruckAvailability availability = stub().checkAvailability(
                    GetTruckRequest.newBuilder().setTruckId(truckId).build());
            return new FleetAvailability(
                    availability.getTruckId(),
                    availability.getAvailableCapacityKg(),
                    availability.getMaxCapacityKg());
        } catch (StatusRuntimeException e) {
            if (e.getStatus().getCode() == Status.Code.NOT_FOUND) {
                throw new ResourceNotFoundException("Camión no encontrado id: " + truckId);
            }
            throw translate(e);
        }
    }

    private TruckServiceGrpc.TruckServiceBlockingStub stub() {
        return truckServiceBlockingStub.withDeadlineAfter(DEADLINE_SECONDS, TimeUnit.SECONDS);
    }

    private String describeFailure(StatusRuntimeException e) {
        String description = e.getStatus().getDescription();
        return description != null ? description : "Flota rechazó la operación: " + e.getStatus().getCode();
    }

    private RuntimeException translate(StatusRuntimeException e) {
        Status.Code code = e.getStatus().getCode();
        String grpcStatus = code.name();
        switch (code) {
            case UNAVAILABLE:
                nudgeReconnect();
                return new FleetServiceUnavailableException("El servicio de Flota no está disponible", grpcStatus, e);
            case DEADLINE_EXCEEDED:
                return new FleetServiceUnavailableException(
                        "El servicio de Flota no respondió dentro de " + DEADLINE_SECONDS + " s", grpcStatus, e);
            case RESOURCE_EXHAUSTED:
                return new FleetServiceUnavailableException("El servicio de Flota está saturado", grpcStatus, e);
            default:
                return new FleetServiceException("Flota respondió con un error inesperado: " + grpcStatus, grpcStatus, e);
        }
    }

    /**
     * Tras una caída, el canal reintenta la conexión con backoff exponencial (hasta ~2 min). Se reinicia el
     * backoff (como máximo una vez por segundo) para que Despachos se recupere apenas Flota vuelva.
     */
    private void nudgeReconnect() {
        long now = System.currentTimeMillis();
        long last = lastReconnectNudge.get();
        if (now - last >= RECONNECT_NUDGE_INTERVAL_MS && lastReconnectNudge.compareAndSet(last, now)) {
            channel.resetConnectBackoff();
        }
    }
}
