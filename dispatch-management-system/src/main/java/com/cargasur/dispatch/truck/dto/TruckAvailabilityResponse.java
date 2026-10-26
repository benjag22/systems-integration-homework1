package com.cargasur.dispatch.truck.dto;

public record TruckAvailabilityResponse(
        Integer truckId,
        Integer availableCapacityKg,
        Integer maxCapacityKg
) {}
