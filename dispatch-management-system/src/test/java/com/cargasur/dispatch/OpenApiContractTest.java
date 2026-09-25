package com.cargasur.dispatch;

import com.cargasur.dispatch.client.controller.ClientController;
import com.cargasur.dispatch.client.dto.ClientResponse;
import com.cargasur.dispatch.client.dto.CreateClientRequest;
import com.cargasur.dispatch.client.service.ClientService;
import com.cargasur.dispatch.shipment.controller.ShipmentController;
import com.cargasur.dispatch.shipment.dto.CreateShipmentRequest;
import com.cargasur.dispatch.shipment.dto.ShipmentLink;
import com.cargasur.dispatch.shipment.dto.ShipmentResponse;
import com.cargasur.dispatch.shipment.service.ShipmentService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.core.util.Yaml;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.io.File;
import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

class OpenApiContractTest {

    private static final String AUTHORIZATION = "Basic ZGVtbzpkZW1v";
    private static final String IDEMPOTENCY_KEY = "openapi-contract-test";
    private static final ObjectMapper JSON = new ObjectMapper();
    private static OpenAPI contract;

    private final ShipmentService shipmentService = mock(ShipmentService.class);
    private final ClientService clientService = mock(ClientService.class);
    private MockMvc mockMvc;

    @BeforeAll
    static void loadContract() throws Exception {
        File spec = new File("openapi.yaml");
        if (!spec.isFile()) {
            spec = new File("dispatch-management-system/openapi.yaml");
        }
        assertTrue(spec.isFile(), "No se encontró dispatch-management-system/openapi.yaml");
        contract = Yaml.mapper().readValue(spec, OpenAPI.class);
    }

    @BeforeEach
    void setUpMvc() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                new ShipmentController(shipmentService),
                new ClientController(clientService)
        ).build();
    }

    @Test
    void shipmentCollectionGetMatchesOpenApi() throws Exception {
        when(shipmentService.getAllShipments()).thenReturn(List.of(shipment(10, "REGISTERED"), shipment(11, "REVERTED")));

        MvcResult result = mockMvc.perform(get("/despachos").header("Authorization", AUTHORIZATION)).andReturn();

        assertResponseMatchesContract("/despachos", HttpMethod.GET, 200, result);
        JsonNode items = JSON.readTree(result.getResponse().getContentAsString());
        assertTrue(items.get(0).path("_links").has("revert"));
        assertFalse(items.get(1).path("_links").has("revert"));
    }

    @Test
    void shipmentCreateMatchesOpenApiRequestAndResponse() throws Exception {
        String body = "{\"clientId\":1,\"truckId\":2,\"weightKg\":100,\"detail\":\"Carga de prueba\"}";
        when(shipmentService.createShipment(eq(IDEMPOTENCY_KEY), any(CreateShipmentRequest.class)))
                .thenReturn(shipment(10, "REGISTERED"));

        Operation operation = operation("/despachos", HttpMethod.POST);
        assertRequiredHeader(operation, "Idempotency-Key");
        assertRequestMatchesContract(operation, body);
        MvcResult result = mockMvc.perform(post("/despachos")
                .header("Authorization", AUTHORIZATION)
                .header("Idempotency-Key", IDEMPOTENCY_KEY)
                .contentType("application/json")
                .content(body)).andReturn();

        assertResponseMatchesContract("/despachos", HttpMethod.POST, 201, result);
    }

    @Test
    void shipmentByIdGetMatchesOpenApi() throws Exception {
        when(shipmentService.getShipmentById(10)).thenReturn(shipment(10, "REGISTERED"));

        MvcResult result = mockMvc.perform(get("/despachos/10").header("Authorization", AUTHORIZATION)).andReturn();

        assertResponseMatchesContract("/despachos/{id}", HttpMethod.GET, 200, result);
    }

    @Test
    void shipmentRevertMatchesOpenApi() throws Exception {
        when(shipmentService.revertShipment(10)).thenReturn(shipment(10, "REVERTED"));

        MvcResult result = mockMvc.perform(post("/despachos/10/revertir")
                .header("Authorization", AUTHORIZATION)).andReturn();

        assertResponseMatchesContract("/despachos/{id}/revertir", HttpMethod.POST, 200, result);
    }

    @Test
    void clientCollectionGetMatchesOpenApi() throws Exception {
        when(clientService.getAllClients()).thenReturn(List.of(client()));

        MvcResult result = mockMvc.perform(get("/clientes").header("Authorization", AUTHORIZATION)).andReturn();

        assertResponseMatchesContract("/clientes", HttpMethod.GET, 200, result);
    }

    @Test
    void clientCreateMatchesOpenApiRequestAndResponse() throws Exception {
        String body = "{\"name\":\"Cliente de prueba\",\"email\":\"prueba@example.com\",\"phone\":\"123456\"}";
        when(clientService.createClient(any(CreateClientRequest.class))).thenReturn(client());

        Operation operation = operation("/clientes", HttpMethod.POST);
        assertRequestMatchesContract(operation, body);
        MvcResult result = mockMvc.perform(post("/clientes")
                .header("Authorization", AUTHORIZATION)
                .contentType("application/json")
                .content(body)).andReturn();

        assertResponseMatchesContract("/clientes", HttpMethod.POST, 201, result);
    }

    @Test
    void clientByIdGetMatchesOpenApi() throws Exception {
        when(clientService.getClientById(1)).thenReturn(client());

        MvcResult result = mockMvc.perform(get("/clientes/1").header("Authorization", AUTHORIZATION)).andReturn();

        assertResponseMatchesContract("/clientes/{id}", HttpMethod.GET, 200, result);
    }

    private void assertResponseMatchesContract(String path, HttpMethod method, int status, MvcResult result)
            throws Exception {
        assertEquals(status, result.getResponse().getStatus());
        Operation operation = operation(path, method);
        ApiResponse response = operation.getResponses().get(String.valueOf(status));
        assertNotNull(response, "OpenAPI no declara el status " + status + " para " + method + " " + path);
        assertFalse(response.getContent().isEmpty(), "OpenAPI no declara contenido para " + method + " " + path);
        Schema<?> schema = response.getContent().values().iterator().next().getSchema();
        assertNotNull(schema, "OpenAPI no declara un schema para " + method + " " + path);
        validateJson(JSON.readTree(result.getResponse().getContentAsString()), schema,
                method + " " + path, contract);
    }

    private void assertRequestMatchesContract(Operation operation, String requestBody) throws Exception {
        assertNotNull(operation.getRequestBody(), "OpenAPI no declara el request body de " + operation.getOperationId());
        Schema<?> schema = operation.getRequestBody().getContent().get("application/json").getSchema();
        assertNotNull(schema, "OpenAPI no declara el schema del request body de " + operation.getOperationId());
        validateJson(JSON.readTree(requestBody), schema, "request " + operation.getOperationId(), contract);
    }

    private void assertRequiredHeader(Operation operation, String headerName) {
        assertTrue(operation.getParameters().stream().anyMatch(parameter ->
                        "header".equals(parameter.getIn())
                                && headerName.equalsIgnoreCase(parameter.getName())
                                && Boolean.TRUE.equals(parameter.getRequired())),
                "OpenAPI debe declarar el encabezado obligatorio " + headerName);
    }

    private Operation operation(String path, HttpMethod method) {
        PathItem pathItem = contract.getPaths().get(path);
        assertNotNull(pathItem, "OpenAPI no declara la ruta " + path);
        Operation operation = pathItem.readOperationsMap().get(PathItem.HttpMethod.valueOf(method.name()));
        assertNotNull(operation, "OpenAPI no declara " + method + " " + path);
        return operation;
    }

    private static void validateJson(JsonNode value, Schema<?> schema, String location, OpenAPI api) {
        if (schema.get$ref() != null) {
            String schemaName = schema.get$ref().substring(schema.get$ref().lastIndexOf('/') + 1);
            Schema<?> referenced = api.getComponents().getSchemas().get(schemaName);
            assertNotNull(referenced, "Referencia OpenAPI inexistente: " + schema.get$ref());
            validateJson(value, referenced, location, api);
            return;
        }

        String type = schema.getType();
        if ("object".equals(type)) {
            assertTrue(value.isObject(), location + " debe ser object");
            if (schema.getRequired() != null) {
                for (String required : schema.getRequired()) {
                    assertTrue(value.has(required), location + " debe incluir " + required);
                }
            }
            Map<String, Schema> properties = schema.getProperties();
            if (properties != null) {
                properties.forEach((name, propertySchema) -> {
                    if (value.has(name)) {
                        validateJson(value.get(name), propertySchema, location + "." + name, api);
                    }
                });
            }
            if (schema.getAdditionalProperties() instanceof Schema<?> additionalSchema) {
                value.fields().forEachRemaining(entry -> {
                    if (properties == null || !properties.containsKey(entry.getKey())) {
                        validateJson(entry.getValue(), additionalSchema, location + "." + entry.getKey(), api);
                    }
                });
            } else if (Boolean.FALSE.equals(schema.getAdditionalProperties()) && properties != null) {
                value.fieldNames().forEachRemaining(name ->
                        assertTrue(properties.containsKey(name), location + " no admite el campo " + name));
            }
        } else if ("array".equals(type)) {
            assertTrue(value.isArray(), location + " debe ser array");
            if (schema.getMinItems() != null) {
                assertTrue(value.size() >= schema.getMinItems(), location + " no cumple minItems");
            }
            if (schema.getMaxItems() != null) {
                assertTrue(value.size() <= schema.getMaxItems(), location + " no cumple maxItems");
            }
            for (JsonNode item : value) {
                validateJson(item, schema.getItems(), location + "[]", api);
            }
        } else if ("string".equals(type)) {
            assertTrue(value.isTextual(), location + " debe ser string");
            if (schema.getMinLength() != null) {
                assertTrue(value.textValue().length() >= schema.getMinLength(), location + " no cumple minLength");
            }
            if (schema.getMaxLength() != null) {
                assertTrue(value.textValue().length() <= schema.getMaxLength(), location + " no cumple maxLength");
            }
            if ("email".equals(schema.getFormat())) {
                assertTrue(value.textValue().matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"), location + " no es email");
            }
            if ("uri-reference".equals(schema.getFormat())) {
                try {
                    URI.create(value.textValue());
                } catch (IllegalArgumentException exception) {
                    throw new AssertionError(location + " no es uri-reference", exception);
                }
            }
        } else if ("integer".equals(type)) {
            assertTrue(value.isIntegralNumber(), location + " debe ser integer");
            assertNumericBounds(value.decimalValue(), schema, location);
        } else if ("number".equals(type)) {
            assertTrue(value.isNumber(), location + " debe ser number");
            assertNumericBounds(value.decimalValue(), schema, location);
        } else if ("boolean".equals(type)) {
            assertTrue(value.isBoolean(), location + " debe ser boolean");
        }
    }

    private static void assertNumericBounds(BigDecimal value, Schema<?> schema, String location) {
        if (schema.getMinimum() != null) {
            assertTrue(value.compareTo(schema.getMinimum()) >= 0, location + " no cumple minimum");
        }
        if (schema.getMaximum() != null) {
            assertTrue(value.compareTo(schema.getMaximum()) <= 0, location + " no cumple maximum");
        }
    }

    private ShipmentResponse shipment(int id, String status) {
        Map<String, ShipmentLink> links = status.equals("REGISTERED")
                ? Map.of("self", new ShipmentLink("/v1/despachos/" + id),
                        "collection", new ShipmentLink("/v1/despachos"),
                        "revert", new ShipmentLink("/v1/despachos/" + id + "/revertir"))
                : Map.of("self", new ShipmentLink("/v1/despachos/" + id),
                        "collection", new ShipmentLink("/v1/despachos"));
        return new ShipmentResponse(id, 1, 2, 3, 100, "Carga de prueba", status,
                LocalDateTime.of(2026, 1, 1, 10, 0), links);
    }

    private ClientResponse client() {
        return new ClientResponse(1, "Cliente de prueba", "prueba@example.com", "123456");
    }
}
