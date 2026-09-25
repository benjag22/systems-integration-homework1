package com.example.truckfleetsystem.grpc;

import com.example.grpc.proto.GetTruckRequest;
import com.example.grpc.proto.LoadItem;
import com.example.grpc.proto.ListTrucksRequest;
import com.example.grpc.proto.ListTrucksResponse;
import com.example.grpc.proto.LoadTruckRequest;
import com.example.grpc.proto.Truck;
import com.example.grpc.proto.TruckAvailability;
import com.example.grpc.proto.Route;
import com.example.grpc.proto.TruckServiceGrpc;
import com.example.grpc.proto.TruckWithRoutes;
import com.example.grpc.proto.UnloadTruckRequest;
import com.example.truckfleetsystem.entity.TruckEntity;
import com.example.truckfleetsystem.service.TruckLoadService;
import com.example.truckfleetsystem.service.TruckRouteService;
import com.example.truckfleetsystem.service.TruckService;
import io.grpc.MethodDescriptor;
import io.grpc.protobuf.ProtoMethodDescriptorSupplier;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.io.InputStream;
import java.time.Duration;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TruckProtoContractTest {

    private static final Map<String, RpcContract> EXPECTED_RPCS = Map.of(
            "GetTruck", new RpcContract("trucks.GetTruckRequest", "trucks.TruckWithRoutes"),
            "LoadTruck", new RpcContract("trucks.LoadTruckRequest", "trucks.Truck"),
            "UnloadTruck", new RpcContract("trucks.UnloadTruckRequest", "trucks.Truck"),
            "ListTrucks", new RpcContract("trucks.ListTrucksRequest", "trucks.ListTrucksResponse"),
            "CheckAvailability", new RpcContract("trucks.GetTruckRequest", "trucks.TruckAvailability")
    );

    private TruckService truckService;
    private TruckLoadService truckLoadService;
    private RedisTemplate<String, byte[]> redisTemplate;
    private ValueOperations<String, byte[]> values;
    private TruckGRPCService grpcService;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUpService() {
        truckService = mock(TruckService.class);
        TruckRouteService truckRouteService = mock(TruckRouteService.class);
        truckLoadService = mock(TruckLoadService.class);
        redisTemplate = mock(RedisTemplate.class);
        values = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(values);
        grpcService = new TruckGRPCService(truckService, truckRouteService, truckLoadService, redisTemplate);
    }

    @Test
    void generatedGrpcDescriptorMatchesProtoRpcContractAndServerImplementation() {
        var generatedService = TruckServiceGrpc.getServiceDescriptor();
        Set<String> generatedNames = generatedService.getMethods().stream()
                .map(TruckProtoContractTest::bareMethodName)
                .collect(Collectors.toSet());
        assertEquals(EXPECTED_RPCS.keySet(), generatedNames);

        for (MethodDescriptor<?, ?> method : generatedService.getMethods()) {
            RpcContract expected = EXPECTED_RPCS.get(bareMethodName(method));
            var protoMethod = ((ProtoMethodDescriptorSupplier) method.getSchemaDescriptor()).getMethodDescriptor();
            assertEquals(expected.requestType(), protoMethod.getInputType().getFullName());
            assertEquals(expected.responseType(), protoMethod.getOutputType().getFullName());
        }

        TruckGRPCService implementation = new TruckGRPCService(null, null, null, null);
        Set<String> boundNames = implementation.bindService().getServiceDescriptor().getMethods().stream()
                .map(TruckProtoContractTest::bareMethodName)
                .collect(Collectors.toSet());
        assertEquals(generatedNames, boundNames);

        Set<String> implementedMethods = Arrays.stream(TruckGRPCService.class.getDeclaredMethods())
                .map(method -> method.getName())
                .collect(Collectors.toSet());
        for (String rpcName : EXPECTED_RPCS.keySet()) {
            String javaMethod = Character.toLowerCase(rpcName.charAt(0)) + rpcName.substring(1);
            assertTrue(implementedMethods.contains(javaMethod), "Falta implementar el RPC " + rpcName);
        }
    }

    @Test
    void protobufMessageFieldNamesNumbersAndWireEncodingMatchContract() throws Exception {
        assertField(GetTruckRequest.getDescriptor(), 1, "truck_id", "INT32");
        assertField(LoadTruckRequest.getDescriptor(), 1, "truck_id", "INT32");
        assertField(LoadTruckRequest.getDescriptor(), 2, "weight_kg", "INT32");
        assertField(LoadTruckRequest.getDescriptor(), 3, "detail", "STRING");
        assertField(UnloadTruckRequest.getDescriptor(), 1, "truck_id", "INT32");
        assertField(UnloadTruckRequest.getDescriptor(), 2, "load_id", "INT32");
        assertField(ListTrucksRequest.getDescriptor(), 0, null, null);

        assertField(Truck.getDescriptor(), 1, "id", "INT32");
        assertField(Truck.getDescriptor(), 2, "license_plate", "STRING");
        assertField(Truck.getDescriptor(), 3, "max_capacity_kg", "INT32");
        assertField(Truck.getDescriptor(), 4, "loads", "MESSAGE");
        assertTrue(Truck.getDescriptor().findFieldByNumber(4).isRepeated());
        assertField(LoadItem.getDescriptor(), 1, "id", "INT32");
        assertField(LoadItem.getDescriptor(), 2, "weight_kg", "INT32");
        assertField(LoadItem.getDescriptor(), 3, "detail", "STRING");
        assertField(TruckWithRoutes.getDescriptor(), 1, "truck", "MESSAGE");
        assertField(TruckWithRoutes.getDescriptor(), 2, "routes", "MESSAGE");
        assertTrue(TruckWithRoutes.getDescriptor().findFieldByNumber(2).isRepeated());
        assertField(Route.getDescriptor(), 1, "id", "INT32");
        assertField(Route.getDescriptor(), 2, "name", "STRING");
        assertField(Route.getDescriptor(), 3, "distance_km", "INT32");
        assertField(Route.getDescriptor(), 4, "origin", "STRING");
        assertField(Route.getDescriptor(), 5, "destination", "STRING");
        assertField(ListTrucksResponse.getDescriptor(), 1, "trucks", "MESSAGE");
        assertTrue(ListTrucksResponse.getDescriptor().findFieldByNumber(1).isRepeated());
        assertField(TruckAvailability.getDescriptor(), 1, "truck_id", "INT32");
        assertField(TruckAvailability.getDescriptor(), 2, "available_capacity_kg", "INT32");
        assertField(TruckAvailability.getDescriptor(), 3, "max_capacity_kg", "INT32");

        TruckAvailability availability = TruckAvailability.newBuilder()
                .setTruckId(17)
                .setAvailableCapacityKg(900)
                .setMaxCapacityKg(1_000)
                .build();
        assertEquals(availability, TruckAvailability.parseFrom(availability.toByteArray()));
    }

    @Test
    void checkAvailabilityImplementationReturnsAProtoContractResponseOverItsMarshaller() {
        TruckEntity truck = new TruckEntity();
        truck.setId(17);
        truck.setLicensePlate("ABCD12");
        truck.setMaxCapacityKg(1_000);
        when(values.get("truck:17")).thenReturn(null);
        when(truckService.getTruck(17)).thenReturn(Optional.of(truck));
        when(truckLoadService.calculateTruckAvailability(truck)).thenReturn(900);

        RecordingObserver<TruckAvailability> observer = new RecordingObserver<>();
        grpcService.checkAvailability(GetTruckRequest.newBuilder().setTruckId(17).build(), observer);

        assertNull(observer.error);
        assertTrue(observer.completed);
        assertEquals(1, observer.values.size());
        TruckAvailability response = observer.values.getFirst();
        assertEquals(17, response.getTruckId());
        assertEquals(900, response.getAvailableCapacityKg());
        assertEquals(1_000, response.getMaxCapacityKg());

        MethodDescriptor<GetTruckRequest, TruckAvailability> method =
                TruckServiceGrpc.getCheckAvailabilityMethod();
        InputStream wireResponse = method.getResponseMarshaller().stream(response);
        assertEquals(response, method.getResponseMarshaller().parse(wireResponse));
        org.mockito.Mockito.verify(values).set(eq("truck:17"), any(byte[].class), eq(Duration.ofHours(1)));
    }

    private static String bareMethodName(MethodDescriptor<?, ?> method) {
        String fullName = method.getFullMethodName();
        return fullName.substring(fullName.lastIndexOf('/') + 1);
    }

    private static void assertField(
            com.google.protobuf.Descriptors.Descriptor message,
            int number,
            String expectedName,
            String expectedType
    ) {
        if (number == 0) {
            assertTrue(message.getFields().isEmpty());
            return;
        }
        var field = message.findFieldByNumber(number);
        assertNotNull(field, "Falta el campo número " + number + " en " + message.getFullName());
        assertEquals(expectedName, field.getName());
        assertEquals(expectedType, field.getType().name());
    }

    private record RpcContract(String requestType, String responseType) {}

    private static final class RecordingObserver<T> implements StreamObserver<T> {
        private final java.util.List<T> values = new java.util.ArrayList<>();
        private Throwable error;
        private boolean completed;

        @Override
        public void onNext(T value) {
            values.add(value);
        }

        @Override
        public void onError(Throwable error) {
            this.error = error;
        }

        @Override
        public void onCompleted() {
            completed = true;
        }
    }
}
