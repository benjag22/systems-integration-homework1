package com.cargasur.dispatch.shipment.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateShipmentRequest(
        @NotNull(message = "El cliente es obligatorio")
        Integer clientId,

        @NotNull(message = "El id del camión es obligatorio")
        Integer truckId,

        @NotNull(message = "El peso es obligatorio")
        @Min(value = 1, message = "El peso debe ser mayor a 0 kg")
        Integer weightKg,

        @NotBlank(message = "El detalle no puede estar vacío")
        @Size(max = 300)
        String detail
) {}