package com.cargasur.dispatch.client.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateClientRequest(
        @NotBlank(message = "El nombre no puede estar vacío")
        @Size(max = 150)
        String name,

        @NotBlank(message = "El email no puede estar vacío")
        @Email(message = "Debe ser un email válido")
        @Size(max = 150)
        String email,

        @Size(max = 20)
        String phone
) {}