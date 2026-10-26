package com.cargasur.dispatch.shipment.client;

public interface FleetCapacityClient {
    FleetReservationResult verifyAndReserveCapacity(Integer truckId, Integer weightKg);
    void releaseCapacity(Integer truckId, Integer loadId, Integer weightKg);

    FleetAvailability checkAvailability(Integer truckId);

    record FleetReservationResult(boolean success, Integer loadId, String message) {}

    record FleetAvailability(Integer truckId, Integer availableCapacityKg, Integer maxCapacityKg) {}
}
