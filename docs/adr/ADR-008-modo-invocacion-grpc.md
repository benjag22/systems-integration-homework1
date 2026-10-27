# ADR-008 - Modo de invocación gRPC: unario

**Estado:** Aceptada

## Contexto

gRPC ofrece cuatro modos: unario, server-streaming, client-streaming y bidireccional. Hay que elegir el de cada RPC de
Flota (`GetTruck`, `ListTrucks`, `LoadTruck`, `UnloadTruck`, `CheckAvailability`).

## Alternativas consideradas

- **Unario para todas.** Una solicitud, una respuesta.
- **Server-streaming para `ListTrucks`:** Flota enviaría un camión por mensaje; útil si la lista es grande o se quiere
  mostrar de a poco.
- **Client-streaming / bidireccional para cargas en lote:** un flujo de `LoadTruckRequest` con respuestas parciales.

## Decisión

Todas las RPC son unarias, invocadas de forma bloqueante con deadline de 2 s desde Despachos.

## Justificación

- `LoadTruck`, `UnloadTruck` y `CheckAvailability` son decisiones de petición-respuesta: Despachos necesita el resultado
  (con éxito o error tipado) antes de continuar, y el modo unario hace que el deadline y los códigos de estado (ADR-004)
  apliquen a una llamada completa. Un flujo complicaría la atomicidad: cada reserva debe ser una transacción
  independiente (ADR-006).
- `ListTrucks` devuelve una flota acotada (decenas de camiones, cada uno con pocas cargas y rutas): cabe en un mensaje
  muy por debajo del límite de 4 MB por defecto y no justifica la complejidad de streaming ni de manejo de contrapresión.
- El lote no se necesita: cada despacho reserva un camión.

## Costo aceptado

Si la flota o sus cargas crecen a miles de elementos, `ListTrucks` unario puede acercarse al límite de tamaño de mensaje
y bloquear al cliente hasta tener la lista completa.

## Consecuencias

Si eso ocurre, se agrega `rpc ListTrucksStream (ListTrucksRequest) returns (stream TruckWithRoutes)` como cambio aditivo
(ADR-003), sin tocar a los consumidores de `ListTrucks`, y se marca el unario como `deprecated`.
