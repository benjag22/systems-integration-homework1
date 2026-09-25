package com.cargasur.dispatch.shipment.client;

import com.example.grpc.proto.TruckServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
public class GrpcClientConfig {

    private ManagedChannel channel;

    @Bean
    public TruckServiceGrpc.TruckServiceBlockingStub truckServiceBlockingStub(
            @Value("${fleet.grpc.host}") String host,
            @Value("${fleet.grpc.port}") int port
    ) {
        this.channel = ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext()
                .build();
        return TruckServiceGrpc.newBlockingStub(channel);
    }

    @PreDestroy
    public void shutdown() {
        if (channel != null) {
            channel.shutdownNow();
            try {
                channel.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
