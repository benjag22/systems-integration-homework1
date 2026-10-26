package com.cargasur.dispatch;

import com.cargasur.dispatch.config.GlobalExceptionHandler;
import com.cargasur.dispatch.config.exception.ConflictException;
import com.cargasur.dispatch.client.controller.ClientController;
import com.cargasur.dispatch.client.dto.CreateClientRequest;
import com.cargasur.dispatch.client.service.ClientService;
import com.cargasur.dispatch.shipment.client.FleetServiceException;
import com.cargasur.dispatch.shipment.client.FleetServiceUnavailableException;
import com.cargasur.dispatch.shipment.controller.ShipmentController;
import com.cargasur.dispatch.shipment.dto.CreateShipmentRequest;
import com.cargasur.dispatch.shipment.service.ShipmentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ApiErrorMappingTest {

    private static final String SHIPMENT_BODY =
            "{\"clientId\":1,\"truckId\":2,\"weightKg\":100,\"detail\":\"Carga de prueba\"}";
    private static final String CLIENT_BODY =
            "{\"name\":\"Cliente\",\"email\":\"a@example.com\",\"phone\":null}";

    private final ShipmentService shipmentService = mock(ShipmentService.class);
    private final ClientService clientService = mock(ClientService.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new ShipmentController(shipmentService), new ClientController(clientService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void fleetUnavailableIs503WithRetryAfterAndGrpcStatus() throws Exception {
        when(shipmentService.createShipment(eq("k1"), any(CreateShipmentRequest.class)))
                .thenThrow(new FleetServiceUnavailableException("Flota no disponible", "UNAVAILABLE", null));

        mockMvc.perform(post("/despachos").header("Idempotency-Key", "k1")
                        .contentType("application/json").content(SHIPMENT_BODY))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Retry-After", "5"))
                .andExpect(content().string(containsString("UNAVAILABLE")));
    }

    @Test
    void fleetDeadlineExceededIs503() throws Exception {
        when(shipmentService.createShipment(eq("k2"), any(CreateShipmentRequest.class)))
                .thenThrow(new FleetServiceUnavailableException("Flota lenta", "DEADLINE_EXCEEDED", null));

        mockMvc.perform(post("/despachos").header("Idempotency-Key", "k2")
                        .contentType("application/json").content(SHIPMENT_BODY))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().string(containsString("DEADLINE_EXCEEDED")));
    }

    @Test
    void unexpectedFleetErrorIs502WithoutRetryAfter() throws Exception {
        when(shipmentService.createShipment(eq("k3"), any(CreateShipmentRequest.class)))
                .thenThrow(new FleetServiceException("error inesperado", "INTERNAL", null));

        mockMvc.perform(post("/despachos").header("Idempotency-Key", "k3")
                        .contentType("application/json").content(SHIPMENT_BODY))
                .andExpect(status().isBadGateway())
                .andExpect(header().doesNotExist("Retry-After"));
    }

    @Test
    void insufficientCapacityIs409() throws Exception {
        when(shipmentService.createShipment(eq("k4"), any(CreateShipmentRequest.class)))
                .thenThrow(new IllegalStateException("El camión 2 no tiene capacidad suficiente"));

        mockMvc.perform(post("/despachos").header("Idempotency-Key", "k4")
                        .contentType("application/json").content(SHIPMENT_BODY))
                .andExpect(status().isConflict())
                .andExpect(header().doesNotExist("Retry-After"));
    }

    @Test
    void missingIdempotencyKeyIs400() throws Exception {
        mockMvc.perform(post("/despachos").contentType("application/json").content(SHIPMENT_BODY))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("Idempotency-Key")));
    }

    @Test
    void duplicateClientEmailIs409() throws Exception {
        when(clientService.createClient(any(CreateClientRequest.class)))
                .thenThrow(new ConflictException("El email ya se encuentra registrado"));

        mockMvc.perform(post("/clientes").contentType("application/json").content(CLIENT_BODY))
                .andExpect(status().isConflict());
    }
}
