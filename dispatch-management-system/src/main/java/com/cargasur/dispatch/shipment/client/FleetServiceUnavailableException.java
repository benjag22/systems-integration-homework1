package com.cargasur.dispatch.shipment.client;

public class FleetServiceUnavailableException extends RuntimeException {
    private final String grpcStatus;

    public FleetServiceUnavailableException(String message, String grpcStatus, Throwable cause) {
        super(message, cause);
        this.grpcStatus = grpcStatus;
    }

    public String getGrpcStatus() {
        return grpcStatus;
    }
}
