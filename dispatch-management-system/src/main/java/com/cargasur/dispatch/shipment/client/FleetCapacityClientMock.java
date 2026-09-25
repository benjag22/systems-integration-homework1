package com.cargasur.dispatch.shipment.client;

import org.springframework.stereotype.Component;
import java.util.Random;

@Component
public class FleetCapacityClientMock implements FleetCapacityClient {

    @Override
    public FleetReservationResult verifyAndReserveCapacity(Integer truckId, Integer weightKg) {
        Integer generatedLoadId = new Random().nextInt(1000, 9999);
        return new FleetReservationResult(true, generatedLoadId, "Capacidad confirmada en Flota (Mock)");
    }

    @Override
    public void releaseCapacity(Integer truckId, Integer loadId, Integer weightKg) {
    }
}