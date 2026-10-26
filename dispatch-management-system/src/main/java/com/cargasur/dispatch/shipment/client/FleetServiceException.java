package com.cargasur.dispatch.shipment.client;

public class FleetServiceException extends RuntimeException {
    private final String grpcStatus;

    public FleetServiceException(String message, String grpcStatus, Throwable cause) {
        super(message, cause);
        this.grpcStatus = grpcStatus;
    }

    public String getGrpcStatus() {
        return grpcStatus;
    }
}
