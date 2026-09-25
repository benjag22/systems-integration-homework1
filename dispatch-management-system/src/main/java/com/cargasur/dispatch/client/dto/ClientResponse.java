package com.cargasur.dispatch.client.dto;

public record ClientResponse(
        Integer id,
        String name,
        String email,
        String phone
) {}