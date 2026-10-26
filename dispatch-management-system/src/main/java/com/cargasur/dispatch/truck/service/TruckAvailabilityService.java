package com.cargasur.dispatch.truck.service;

import com.cargasur.dispatch.shipment.client.FleetCapacityClient;
import com.cargasur.dispatch.truck.dto.TruckAvailabilityResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TruckAvailabilityService {

    private final FleetCapacityClient fleetCapacityClient;

    public TruckAvailabilityResponse getAvailability(Integer truckId) {
        var availability = fleetCapacityClient.checkAvailability(truckId);
        return new TruckAvailabilityResponse(
                availability.truckId(),
                availability.availableCapacityKg(),
                availability.maxCapacityKg());
    }
}
