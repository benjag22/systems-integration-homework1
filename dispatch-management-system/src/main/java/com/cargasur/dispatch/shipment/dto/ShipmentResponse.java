package com.cargasur.dispatch.shipment.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import java.util.Map;

public record ShipmentResponse(

        Integer id,

        Integer clientId,

        Integer truckId,

        Integer loadId,

        Integer weightKg,

        String detail,

        String status,

        LocalDateTime createdAt,

        @JsonProperty("_links")
        Map<String, ShipmentLink> links

) {}