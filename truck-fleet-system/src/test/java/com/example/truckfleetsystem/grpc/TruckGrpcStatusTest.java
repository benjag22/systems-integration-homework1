package com.example.truckfleetsystem.grpc;

import com.example.grpc.proto.LoadTruckRequest;
import com.example.grpc.proto.Truck;
import com.example.grpc.proto.UnloadTruckRequest;
import com.example.truckfleetsystem.entity.TruckEntity;
import com.example.truckfleetsystem.entity.TruckLoadEntity;
import com.example.truckfleetsystem.service.InsufficientCapacityException;
import com.example.truckfleetsystem.service.LoadNotFoundException;
import com.example.truckfleetsystem.service.LoadTruckMismatchException;
import com.example.truckfleetsystem.service.TruckLoadService;
import com.example.truckfleetsystem.service.TruckNotFoundException;
import com.example.truckfleetsystem.service.TruckRouteService;
import com.example.truckfleetsystem.service.TruckService;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TruckGrpcStatusTest {

    private TruckService truckService;
    private TruckLoadService truckLoadService;
    private RedisTemplate<String, byte[]> redisTemplate;
    private TruckGRPCService grpcService;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        truckService = mock(TruckService.class);
        truckLoadService = mock(TruckLoadService.class);
        redisTemplate = mock(RedisTemplate.class);
        grpcService = new TruckGRPCService(truckService, mock(TruckRouteService.class), truckLoadService, redisTemplate);
    }

    @Test
    void loadTruckReportsCreatedLoadAndEvictsCache() {
        TruckEntity truck = truck(2, 2000);
        TruckLoadEntity created = new TruckLoadEntity("Reserva", 100, truck);
        created.setId(42);
        when(truckService.loadTruck(2, "Reserva", 100)).thenReturn(created);
        when(truckLoadService.findAllByTruck(truck)).thenReturn(List.of(created));

        Recorder<Truck> observer = new Recorder<>();
        grpcService.loadTruck(request(2, 100, "Reserva"), observer);

        assertNull(observer.error);
        assertTrue(observer.completed);
        Truck response = observer.values.get(0);
        assertTrue(response.hasCreatedLoad());
        assertEquals(42, response.getCreatedLoad().getId());
        assertEquals(100, response.getCreatedLoad().getWeightKg());
        verify(redisTemplate).delete("truck:2");
    }

    @Test
    void loadTruckWithoutCapacityIsFailedPrecondition() {
        when(truckService.loadTruck(1, "x", 500)).thenThrow(new InsufficientCapacityException("sin capacidad"));
        assertEquals(Status.Code.FAILED_PRECONDITION, loadCode(1, 500, "x"));
    }

    @Test
    void loadTruckWithInvalidWeightIsInvalidArgument() {
        when(truckService.loadTruck(1, "x", 0)).thenThrow(new IllegalArgumentException("peso"));
        assertEquals(Status.Code.INVALID_ARGUMENT, loadCode(1, 0, "x"));
    }

    @Test
    void loadTruckOnMissingTruckIsNotFound() {
        when(truckService.loadTruck(99, "x", 10)).thenThrow(new TruckNotFoundException(99));
        assertEquals(Status.Code.NOT_FOUND, loadCode(99, 10, "x"));
    }

    @Test
    void loadTruckUnexpectedErrorIsInternal() {
        when(truckService.loadTruck(1, "x", 10)).thenThrow(new RuntimeException("boom"));
        assertEquals(Status.Code.INTERNAL, loadCode(1, 10, "x"));
    }

    @Test
    void unloadLoadFromAnotherTruckIsFailedPrecondition() {
        when(truckService.unloadTruck(1, 7)).thenThrow(new LoadTruckMismatchException(7, 1));
        assertEquals(Status.Code.FAILED_PRECONDITION, unloadCode(1, 7));
    }

    @Test
    void unloadMissingLoadIsNotFound() {
        when(truckService.unloadTruck(1, 7)).thenThrow(new LoadNotFoundException(7));
        assertEquals(Status.Code.NOT_FOUND, unloadCode(1, 7));
    }

    private Status.Code loadCode(int truckId, int weight, String detail) {
        Recorder<Truck> observer = new Recorder<>();
        grpcService.loadTruck(request(truckId, weight, detail), observer);
        assertNotNull(observer.error);
        return Status.fromThrowable(observer.error).getCode();
    }

    private Status.Code unloadCode(int truckId, int loadId) {
        Recorder<Truck> observer = new Recorder<>();
        grpcService.unloadTruck(UnloadTruckRequest.newBuilder().setTruckId(truckId).setLoadId(loadId).build(), observer);
        assertNotNull(observer.error);
        return Status.fromThrowable(observer.error).getCode();
    }

    private static LoadTruckRequest request(int truckId, int weight, String detail) {
        return LoadTruckRequest.newBuilder().setTruckId(truckId).setWeightKg(weight).setDetail(detail).build();
    }

    private static TruckEntity truck(int id, int capacity) {
        TruckEntity truck = new TruckEntity();
        truck.setId(id);
        truck.setLicensePlate("TEST-" + id);
        truck.setMaxCapacityKg(capacity);
        return truck;
    }

    private static final class Recorder<T> implements StreamObserver<T> {
        private final List<T> values = new ArrayList<>();
        private Throwable error;
        private boolean completed;

        @Override
        public void onNext(T value) {
            values.add(value);
        }

        @Override
        public void onError(Throwable t) {
            error = t;
        }

        @Override
        public void onCompleted() {
            completed = true;
        }
    }
}
