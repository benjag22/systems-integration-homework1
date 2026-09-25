package com.cargasur.dispatch.shipment.dto;

import java.time.LocalDateTime;

public record ShipmentResponse(
        Integer id,
        Integer clientId,
        Integer truckId,
        Integer loadId,
        Integer weightKg,
        String detail,
        String status,
        LocalDateTime createdAt
) {}
