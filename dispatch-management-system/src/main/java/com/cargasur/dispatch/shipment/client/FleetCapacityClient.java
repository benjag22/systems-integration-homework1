package com.cargasur.dispatch.shipment.client;

public interface FleetCapacityClient {
    FleetReservationResult verifyAndReserveCapacity(Integer truckId, Integer weightKg);
    void releaseCapacity(Integer truckId, Integer loadId, Integer weightKg);

    record FleetReservationResult(boolean success, Integer loadId, String message) {}
}