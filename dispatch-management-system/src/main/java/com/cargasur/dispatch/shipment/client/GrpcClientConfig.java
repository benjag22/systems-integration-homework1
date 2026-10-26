package com.cargasur.dispatch.shipment.client;

import com.example.grpc.proto.TruckServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GrpcClientConfig {

    @Bean(destroyMethod = "shutdownNow")
    public ManagedChannel fleetChannel(
            @Value("${fleet.grpc.host}") String host,
            @Value("${fleet.grpc.port}") int port
    ) {
        return ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext()
                .build();
    }

    @Bean
    public TruckServiceGrpc.TruckServiceBlockingStub truckServiceBlockingStub(ManagedChannel fleetChannel) {
        return TruckServiceGrpc.newBlockingStub(fleetChannel);
    }
}
