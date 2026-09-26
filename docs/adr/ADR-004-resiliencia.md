# ADR-004 - Qué hace Despachos cuando Flota está caída o lenta

**Estado:** Aceptada

## Contexto

Despachos depende de una llamada gRPC a Flota para poder registrar o revertir un despacho. Esa dependencia puede fallar
de dos formas distintas, si Flota está caída (conexión rechazada) o si Flota está viva, pero lenta (no responde a
tiempo). Sin una decisión explícita, el llamador podría quedar colgado indefinidamente o devolver un 500 genérico que no
distingue entre "tu pedido es inválido" y "no pudimos verificarlo".

## Alternativas consideradas

- **Opción A - Esperar indefinidamente:** No define timeout, si Flota nunca responde, entonces la petición HTTP tampoco.
  Esto es inaceptable, un cliente lento encadenado también cuelga a Despachos.
- **Opción B - Timeout corto + 503 con `Retry-After`, sin reintento automático:** El código gRPC distingue por qué falló
  y lo traduce a un código HTTP semánticamente distinto de un conflicto de negocio.
- **Opción C - Reintentar automáticamente 2-3 veces antes de fallar:** Reduce fallas transitorias, pero en un pico de
  carga real amplifica la presión sobre un Flota ya lento (retry storm) y complica la idempotencia del lado de Flota
  (`LoadTruck` no es idempotente, dado que dos ejecuciones exitosas crean dos cargas).

## Decisión

Opción B: `withDeadlineAfter(2s)` en el stub bloqueante de gRPC (`GrpcFleetCapacityClient`). Si el código gRPC es
`UNAVAILABLE` o`DEADLINE_EXCEEDED`, Despachos responde `503 Service Unavailable` con encabezado `Retry-After: 5` y
cuerpo `application/problem+json`. Si el código es `NOT_FOUND` (camión inexistente) o `FAILED_PRECONDITION`(capacidad
insuficiente), se trata como conflicto de negocio (`409`), no como falla de infraestructura.

## Justificación

Separar "Flota no está disponible" (503, reintentable) de "el camión no tiene capacidad" (409, no reintentable sin
cambiar el pedido) evita que un cliente reintente ciegamente un pedido que va a fallar siempre, y evita que un cliente
abandone un pedido que sí podría prosperar si reintenta en unos segundos. Dos segundos de deadline se eligen porque la
consulta de disponibilidad es de alto volumen. Un timeout más largo degrada la API pública completa ante cualquier
lentitud de Flota, mientras que uno más corto arriesga falsos negativos bajo carga normal.

## Costo aceptado

Con Opción B, una lentitud transitoria de Flota (por ejemplo, un GC pause de 2.5s) se reporta como 503 aunque Flota se
hubiera recuperado al instante siguiente. Se acepta ese falso negativo ocasional a cambio de que Despachos nunca quede
bloqueado esperando. El costo se puede medir reproduciendo el experimento de timeout de la sección de Competencia 6 de
este repositorio: bajar `fleet-api` con `docker compose stop fleet-api` y observar el `503` y su latencia (≈2s) contra
un `POST /v1/despachos`.

## Consecuencias

Cualquier consumidor de la API pública (portal web) debe manejar `503` como "reintentable tras Retry-After", distinto de
`409` ("cambia el pedido"). Si en el futuro se agrega un circuit breaker, debe abrir el circuito sobre la tasa de
`UNAVAILABLE`/`DEADLINE_EXCEEDED`, no sobre la de `NOT_FOUND`.
