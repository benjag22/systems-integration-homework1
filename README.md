# CargaSur: Integración Despachos - Flota (Forma L)

Dos servicios independientes que antes no se hablaban:

- **`dispatch-management-system`:** API REST pública (`/v1`), Postgres propio.
- **`truck-fleet-system`:** servicio interno gRPC (puerto 9090, solo red interna), Postgres propio, caché Redis.
- **`truck-fleet-client-dotnet`:** segundo cliente gRPC en .NET (O5), incluido en el compose bajo el perfil `clients`.

Contratos versionados en el repo: [`dispatch-management-system/openapi.yaml`](dispatch-management-system/openapi.yaml)
(REST) y [`proto/trucks.proto`](proto/trucks.proto) (gRPC, **fuente única** compilada por Flota, Despachos y el cliente
.NET). Cambios de contratos: [`docs/CHANGELOG-contratos.md`](docs/CHANGELOG-contratos.md).

## Levantar todo el sistema

Un solo comando levanta todos los servicios (2 APIs, 2 Postgres, 1 Redis):

```bash
docker compose up -d --build
```

- API REST: `http://localhost:8080/v1` (Basic Auth, ver abajo)
- Swagger UI: `http://localhost:8080/v1/docs`
- gRPC de Flota: **no se publica al host** (es interno; sin autenticación). Para depurar con Postman o grpcurl:
  `docker compose -f docker-compose.yml -f docker-compose.debug.yml up -d` lo expone solo en `127.0.0.1:9090`.

Credenciales (ADR-005): `demo`/`demo123` (rol OPERATOR: lee y escribe) y `viewer`/`viewer123` (rol VIEWER: solo GET;
escribir responde 403). Se cambian con `AUTH_USERNAME`, `AUTH_PASSWORD`, `AUTH_VIEWER_USERNAME`, `AUTH_VIEWER_PASSWORD`.

Para bajar todo: `docker compose down -v`.

## Probar la integración

```bash
curl -s -u demo:demo123 -X POST http://localhost:8080/v1/clientes \
  -H 'Content-Type: application/json' -d '{"name":"Cliente","email":"c@ejemplo.cl"}'

curl -s -u demo:demo123 -X POST http://localhost:8080/v1/despachos \
  -H 'Content-Type: application/json' -H 'Idempotency-Key: demo-1' \
  -d '{"clientId":1,"truckId":1,"weightKg":100,"detail":"prueba"}'

curl -s -u demo:demo123 http://localhost:8080/v1/camiones/1/disponibilidad
```

Errores que distingue la API (ADR-004): **409** falta de capacidad o conflicto de negocio (no reintentar), **503 +
`Retry-After`** Flota caída o lenta (`grpcStatus` = `UNAVAILABLE` o `DEADLINE_EXCEEDED`), **502** error inesperado de
Flota.

```bash
docker compose stop fleet-api      # caída: 503 inmediato (UNAVAILABLE)
docker compose start fleet-api     # se recupera sin reiniciar dispatch-api
docker compose pause fleet-api     # lentitud: 503 a ~2 s (DEADLINE_EXCEEDED)
docker compose unpause fleet-api
docker compose --profile clients run --rm fleet-client list   # cliente .NET
```

## Experimento

`experimento.py` administra `docker compose` por su cuenta y ejecuta: baseline, Flota detenida, recuperación, Flota
pausada, recuperación y 20 reservas simultáneas sobre un camión de capacidad limitada. Se
usa [uv](https://docs.astral.sh/uv).

```bash
uv sync
uv run experimento.py                      # reinicia el stack (down -v, up --build)
uv run experimento.py --no-compose-reset   # usa el stack ya levantado
uv run experimento.py --plot-from-csv docs/experimento/resultados_crudos.csv
```

Salidas versionadas en `docs/experimento/`: `resultados_crudos.csv`, `resumen.md` (tablas y checks de invariantes) y
`latencia.png`.

## Pruebas de contrato

```bash
cd dispatch-management-system && ./mvnw -Dtest='OpenApiContractTest,ApiErrorMappingTest' test
cd truck-fleet-system && ./gradlew test --tests '*TruckProtoContractTest' --tests '*TruckGrpcStatusTest'
```

## Decisiones de diseño (ADR)

- [ADR-001 - Dos servicios, no un monolito](docs/adr/ADR-001-descomposicion-servicios.md)
- [ADR-002 - REST hacia afuera, gRPC hacia adentro](docs/adr/ADR-002-rest-vs-grpc.md)
- [ADR-003 - Versionado, evolución y fuente única de los contratos](docs/adr/ADR-003-versionado-contrato.md)
- [ADR-004 - Resiliencia ante Flota caída, lenta o con error](docs/adr/ADR-004-resiliencia.md)
- [ADR-005 - Autenticación y roles](docs/adr/ADR-005-autenticacion.md)
- [ADR-006 - Atomicidad de la reserva de capacidad (condición de carrera)](docs/adr/ADR-006-atomicidad-capacidad.md)
- [ADR-007 - Factor de contexto: impacto operacional y seguridad](docs/adr/ADR-007-factor-de-contexto.md)
- [ADR-008 - Modo de invocación gRPC: unario](docs/adr/ADR-008-modo-invocacion-grpc.md)

## Requisitos cubiertos

| #  | Requisito                                        | Dónde                                                                          |
|----|--------------------------------------------------|--------------------------------------------------------------------------------|
| T1 | Todo dockerizado, un solo `docker compose up`    | `docker-compose.yml` (raíz)                                                    |
| T2 | REST versionado `/v1`, errores en JSON           | `GlobalExceptionHandler`, `SecurityConfig` (401/403 también en `problem+json`) |
| T3 | Contrato explícito (OpenAPI + .proto)            | `openapi.yaml`, `proto/trucks.proto` (fuente única)                            |
| T4 | Servicio interno en gRPC consumido por Despachos | `GrpcFleetCapacityClient` (unario, deadline 2 s, ADR-008)                      |
| T5 | Base de datos por servicio                       | `dispatch-postgres` / `fleet-postgres` separados                               |
| T6 | Autenticación REST                               | HTTP Basic con roles OPERATOR/VIEWER (ADR-005)                                 |
| T7 | Manejo de fallas del gRPC                        | 503 / 409 / 502 según código gRPC; reconexión inmediata (ADR-004)              |
| O1 | Caché Redis con invalidación                     | Flota (`CheckAvailability`), usada por `GET /v1/camiones/{id}/disponibilidad`  |
| O2 | Idempotencia                                     | `Idempotency-Key` en `ShipmentService`                                         |
| O3 | HATEOAS                                          | `_links` en `ShipmentResponse`                                                 |
| O4 | Pruebas de contrato                              | `OpenApiContractTest` (OpenAPI 3.0.3), `TruckProtoContractTest`                |
| O5 | Segundo cliente gRPC                             | `truck-fleet-client-dotnet` (perfil `clients` del compose)                     |

## Uso de asistentes de IA

Se usó Claude (Anthropic) como asistente de programación para implementar el cliente gRPC de Despachos hacia Flota,
unificar el despliegue Docker en un único `docker-compose.yml`, redactar los ADR y escribir `experimento.py`. En la
corrección posterior a la revisión del docente, Claude se usó además para rehacer la reserva atómica de capacidad en
Flota (bloqueo de fila) y los ADR 005 a 008. Todo el código generado fue revisado antes de incluirse.
