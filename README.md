# CargaSur: Integración Despachos - Flota (Forma L)

Dos servicios independientes que antes no se hablaban:

- **`dispatch-management-system`:** API REST pública (`/v1`), Postgres propio.
- **`truck-fleet-system`:** servicio interno gRPC (puerto 9090), Postgres propio, caché Redis.
- **`truck-fleet-client-dotnet`:** segundo cliente gRPC en .NET (O5), para probar interoperabilidad.

Contratos versionados en el repo: [`dispatch-management-system/openapi.yaml`](dispatch-management-system/openapi.yaml)
y [`truck-fleet-system/src/main/proto/trucks.proto`](truck-fleet-system/src/main/proto/trucks.proto)
(copiado también en `dispatch-management-system/src/main/proto/` para que Despachos compile su propio stub cliente).

## Levantar todo el sistema

Un solo comando levanta todos los servicios (2 APIs, 2 Postgres, 1 Redis):

```bash
docker compose up -d --build
```

- API REST: `http://localhost:8080/v1` (Basic Auth `demo`/`demo123`, configurable con `AUTH_USERNAME`/`AUTH_PASSWORD`)
- Swagger UI: `http://localhost:8080/v1/docs`
- gRPC de Flota: `localhost:9090` (texto plano, sin TLS)

Para bajar todo:

```bash
docker compose down -v
```

## Probar la integración

```bash
curl -s -u demo:demo123 -X POST http://localhost:8080/v1/despachos \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: demo-1' \
  -d '{"clientId":1,"truckId":1,"weightKg":100,"detail":"prueba"}'
```

Esto dispara la llamada gRPC real a Flota (`LoadTruck`). Para ver el modo de falla ejecute
`docker compose stop fleet-api`, repita la misma llamada con otra `Idempotency-Key` y observe el `503` con `Retry-After`
(ver [`docs/adr/ADR-004-resiliencia.md`](docs/adr/ADR-004-resiliencia.md)).

Detalle de idempotencia, HATEOAS y credenciales:
[`dispatch-management-system/README.md`](dispatch-management-system/README.md).

## Decisiones de diseño (ADR)

- [ADR-001 - Dos servicios, no un monolito](docs/adr/ADR-001-descomposicion-servicios.md)
- [ADR-002 - REST hacia afuera, gRPC hacia adentro](docs/adr/ADR-002-rest-vs-grpc.md)
- [ADR-003 - Versionado y evolución del contrato](docs/adr/ADR-003-versionado-contrato.md)
- [ADR-004 - Resiliencia ante Flota caída o lenta](docs/adr/ADR-004-resiliencia.md)

## Requisitos cubiertos

| #  | Requisito                                     | Dónde                                                                      |
|----|-----------------------------------------------|----------------------------------------------------------------------------|
| T1 | Todo dockerizado, un solo `docker compose up` | `docker-compose.yml` (raíz)                                                |
| T2 | REST versionado `/v1`, errores en JSON        | `dispatch-management-system` (`GlobalExceptionHandler`, `application.yml`) |
| T3 | Contrato explícito (OpenAPI + .proto)         | `openapi.yaml`, `trucks.proto`                                             |
| T4 | gRPC interno consumido por Despachos          | `GrpcFleetCapacityClient` (unary bloqueante con deadline)                  |
| T5 | Base de datos por servicio                    | `dispatch-postgres` / `fleet-postgres` separados                           |
| T6 | Autenticación REST                            | HTTP Basic (`SecurityConfig`)                                              |
| T7 | Manejo de fallas del gRPC                     | `FleetServiceUnavailableException` → `503` + `Retry-After`                 |
| O1 | Caché Redis con invalidación                  | `TruckGRPCService` (Flota)                                                 |
| O2 | Idempotencia                                  | `Idempotency-Key` en `ShipmentService`                                     |
| O3 | HATEOAS                                       | `_links` en `ShipmentResponse`                                             |
| O4 | Pruebas de contrato                           | `OpenApiContractTest`, `TruckProtoContractTest`                            |
| O5 | Segundo cliente gRPC                          | `truck-fleet-client-dotnet`                                                |

## Uso de asistentes de IA

Se usó Claude (Anthropic) como asistente de programación para implementar el cliente gRPC real de Despachos hacia Flota
(antes existía solo un mock), unificar el despliegue Docker en un único `docker-compose.yml`, mapear las fallas de gRPC
a `503`, y redactar los cuatro ADR en `docs/adr/`. Todo el código generado fue revisado antes de incluirse.
