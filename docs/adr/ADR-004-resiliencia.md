# ADR-004 - Qué hace Despachos cuando Flota está caída, lenta o responde un error

**Estado:** Aceptada (revisada tras medir el comportamiento real; ver `docs/experimento/`)

## Contexto

Despachos depende de una llamada gRPC a Flota para registrar o revertir un despacho. Esa dependencia falla de tres
formas distintas, que el cliente de la API debe poder distinguir:

1. **Flota caída** (contenedor detenido): la conexión se rechaza o el nombre no resuelve. gRPC responde `UNAVAILABLE` de
   inmediato, **sin esperar el deadline**.
2. **Flota lenta o congelada** (proceso vivo que no responde): la llamada espera el deadline y falla con
   `DEADLINE_EXCEEDED`.
3. **Flota responde, pero rechaza o falla:** el servidor devuelve un código de negocio (`FAILED_PRECONDITION`,
   `NOT_FOUND`, `INVALID_ARGUMENT`) o un error propio (`INTERNAL`).

## Alternativas consideradas

- **Opción A - Esperar indefinidamente:** inaceptable; una Flota lenta cuelga también a Despachos.
- **Opción B - Deadline corto + traducción por código gRPC, sin reintento automático.**
- **Opción C - Reintentar automáticamente 2-3 veces:** reduce fallas transitorias, pero amplifica la presión sobre una
  Flota ya lenta (retry storm) y `LoadTruck` no es idempotente, por lo que un reintento tras una respuesta perdida
  crearía dos cargas.

## Decisión

Opción B. El stub usa `withDeadlineAfter(2s)` y los errores se traducen así:

| Origen en Flota                           | Código gRPC           | Respuesta de la API                              | ¿Reintentar?          |
|-------------------------------------------|-----------------------|--------------------------------------------------|-----------------------|
| Sin capacidad suficiente                  | `FAILED_PRECONDITION` | **409**                                          | No: cambiar el pedido |
| Camión o carga inexistente                | `NOT_FOUND`           | 409 (404 en `GET /camiones/{id}/disponibilidad`) | No                    |
| Peso o detalle inválido                   | `INVALID_ARGUMENT`    | 409                                              | No                    |
| Flota caída                               | `UNAVAILABLE`         | **503** + `Retry-After: 5`                       | Sí                    |
| Flota lenta (> 2 s)                       | `DEADLINE_EXCEEDED`   | **503** + `Retry-After: 5`                       | Sí                    |
| Flota saturada                            | `RESOURCE_EXHAUSTED`  | 503 + `Retry-After: 5`                           | Sí                    |
| Cualquier otro (`INTERNAL`, `UNKNOWN`...) | otro                  | **502** sin `Retry-After`                        | No a ciegas           |

El cuerpo `application/problem+json` de 502 y 503 incluye `grpcStatus` con el código real, de modo que un 503 nunca se
confunde con un error de otra causa y el experimento puede verificar la causa subyacente.

**Recuperación:** tras una caída el canal gRPC reintenta la conexión con backoff exponencial (hasta ~2 min). Ante cada
`UNAVAILABLE` el cliente llama a `ManagedChannel.resetConnectBackoff()` (como máximo una vez por segundo) para
reconectar apenas Flota vuelva. Las dependencias `io.grpc:*` de Despachos se resuelven a una sola versión (ADR-003); con
versiones mezcladas el canal quedaba inutilizable tras una caída (`NoSuchMethodError`) y Despachos no se recuperaba sin
reiniciarse.

## Justificación

Separar "Flota no está disponible" (503, reintentable) de "el camión no tiene capacidad" (409, no reintentable sin
cambiar el pedido) evita que un cliente reintente ciegamente un pedido que fallará siempre y que abandone uno que
prosperaría en unos segundos. Dos segundos de deadline equilibran el volumen alto de consultas (un timeout largo degrada
toda la API) con los falsos negativos bajo carga normal. La respuesta ante una caída es inmediata y la ante una lentitud
llega a ~2 s: **no se espera que ambas tengan la misma latencia**; eso se verifica en el experimento.

## Costo aceptado

- Una lentitud transitoria se reporta como 503 aunque Flota se hubiera recuperado al instante.
- **Resultado ambiguo:** si Flota confirma `LoadTruck` pero la respuesta no llega antes del deadline, Despachos responde
  503 sin conocer el `loadId`, y un reintento con otra clave crearía una segunda carga (huérfana). Para no ampliar el
  contrato, esto se documenta como límite; la solución natural es un `request_id` idempotente en `LoadTruckRequest`
  (cambio aditivo, ADR-003).
- Despachos mantiene abierta una transacción de base de datos mientras espera a Flota (hasta 2 s).
- Cuando falla el guardado local después de reservar en Flota, Despachos compensa liberando la carga recién creada.

## Consecuencias

Los consumidores de la API tratan 503 como "reintentable tras `Retry-After`", 409 como "cambie el pedido" y 502 como
"avise a soporte". Un circuit breaker futuro debe abrirse según la tasa de `UNAVAILABLE`/`DEADLINE_EXCEEDED`, no de
`FAILED_PRECONDITION`.
