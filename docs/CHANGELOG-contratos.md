# Changelog de contratos

Formato: versión del contrato, tipo de cambio (aditivo / incompatible, según ADR-003) y qué deben hacer los
consumidores.

## 1.1.0 / trucks.proto 1.1 (actual)

- **gRPC, aditivo:** `Truck.created_load = 5` (`LoadItem`). `LoadTruck` informa la carga creada. Los clientes antiguos
  ignoran el campo.
- **gRPC, comportamiento documentado:** `LoadTruck`/`UnloadTruck` emiten `FAILED_PRECONDITION`, `INVALID_ARGUMENT` y
  `NOT_FOUND` en lugar de `INTERNAL` para errores de negocio. `UnloadTruck` verifica que la carga pertenezca al camión.
- **gRPC:** el `.proto` pasa a una única fuente en `proto/trucks.proto`.
- **REST, aditivo:** `GET /v1/camiones/{id}/disponibilidad`; campo `grpcStatus` en los errores 502/503; respuesta 502
  (error inesperado de Flota); respuestas 403 (rol VIEWER) y 401 con cuerpo `application/problem+json`.
- **REST, corrección para alinear con el contrato:** correo de cliente duplicado responde 409 (antes 400); falta de
  capacidad responde 409 (antes 503); `createdAt` se informa también en la respuesta de creación; falta de
  `Idempotency-Key` responde `application/problem+json`.
- **OpenAPI:** el documento pasa a OpenAPI 3.0.3 (`nullable: true`) para ser validable por el test de contrato.

## 1.0.0

- Primera versión: `/v1/clientes`, `/v1/despachos`, `/v1/despachos/{id}/revertir`; `trucks.proto` con cinco RPC unarias.
