package com.cargasur.dispatch.shipment.client;

public class FleetServiceUnavailableException extends RuntimeException {
    public FleetServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
