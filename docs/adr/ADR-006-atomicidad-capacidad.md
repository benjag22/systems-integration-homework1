# ADR-006 - Atomicidad de la reserva de capacidad (condición de carrera)

**Estado:** Aceptada

## Contexto

El problema pide "especial trato con toda posible condición de carrera". La invariante de negocio es: **para cada
camión, Σ peso de sus cargas ≤ `max_capacity_kg`, siempre**.

La condición de carrera es un *check-then-act* en `LoadTruck`: dos solicitudes leen la misma suma de cargas (p. ej.
1.900 kg de 2.000), ambas concluyen que 100 kg caben, y ambas insertan: el camión queda con 2.100 kg. Con 20 solicitudes
simultáneas de 100 kg sobre un camión con 800 kg libres, la primera versión aceptó 11 y dejó 2.300 kg sobre 2.000. Hay
otras carreras:

1. **Reservas del mismo camión** (la anterior): se resuelve en Flota, dueño del dato.
2. **Mismo `Idempotency-Key` repetido en paralelo:** `INSERT ... ON CONFLICT DO NOTHING` sobre la clave; la segunda
   solicitud espera el commit de la primera y devuelve el mismo despacho.
3. **Dos reversiones del mismo despacho:** `SELECT ... FOR UPDATE` sobre el despacho; la segunda ve `REVERTED` y
   recibe 409.
4. **Identificar la carga creada:** Despachos no puede deducir el `loadId` de la lista de cargas (dos despachos recibían
   el mismo id); `LoadTruck` devuelve la carga creada (`created_load`).
5. **Caché de disponibilidad desactualizada:** es solo consultiva; no participa en la decisión de reservar.

## Alternativas consideradas

- **Bloqueo pesimista de la fila del camión** (`SELECT ... FOR UPDATE`) dentro de una transacción que suma y luego
  inserta.
- **Aislamiento SERIALIZABLE con reintentos:** correcto, pero obliga a reintentar ante `40001` y bajo contención genera
  muchos abortos.
- **Bloqueo optimista (`@Version` en `truck`):** exige reintentos y tocar la fila del camión en cada carga.
- **Contador `loaded_kg` con `UPDATE ... WHERE loaded_kg + :w <= max`:** atómico y barato, pero duplica el dato derivado
  de `truck_load` y hay que mantenerlo sincronizado en cada alta y baja.
- **Bloqueo distribuido en Redis:** agrega un componente que puede fallar y no protege la base de datos.

## Decisión

Bloqueo pesimista de la fila del camión. `TruckService.loadTruck` y `unloadTruck` son `@Transactional`: lo primero que
hacen es `SELECT ... FOR UPDATE` del camión (`TruckRepository.findByIdForUpdate`); luego suman las cargas, verifican,
insertan y hacen commit. En `READ COMMITTED` cada sentencia toma un snapshot nuevo, así que la suma posterior al bloqueo
ya ve la carga que confirmó la transacción anterior. Camiones distintos no se bloquean entre sí.

Si no hay capacidad, Flota responde `FAILED_PRECONDITION` (409 en la API, no 503). Tras el commit se **invalida** la
caché del camión (no se escribe el valor nuevo, para que dos escritores no dejen un valor viejo) y la caché usa un TTL
de 60 s que acota la obsolescencia si una invalidación falla o compite con una lectura. Si Redis cae, Flota lee de
PostgreSQL.

## Justificación

La invariante depende de varias filas (`truck_load`), por lo que una restricción `CHECK` no la expresa; serializar por
camión la protege con una transacción corta y sin reintentos. La autoridad es siempre la base de datos del dueño del
dato (ADR-001).

## Costo aceptado

Las reservas del mismo camión se ejecutan en serie (aceptable: el costo de la sección crítica es una suma sobre pocas
filas). La caché de `CheckAvailability` puede estar desactualizada hasta 60 s en carreras raras; por eso es consultiva y
nunca decide una reserva.

## Consecuencias

Todo camino que cambie las cargas de un camión debe tomar el mismo bloqueo. El experimento (`experimento.py`, fase F)
lanza 20 solicitudes simultáneas y verifica la invariante, la consistencia de la disponibilidad final y la unicidad de
los `loadId`.
