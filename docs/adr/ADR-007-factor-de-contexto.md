# ADR-007 - Factor de contexto: impacto operacional y seguridad por sobrecarga de camiones

**Estado:** Aceptada

## Contexto

El enunciado pide analizar un factor de contexto (seguridad, privacidad, costo, accesibilidad, impacto operacional...) y
mostrar cómo moldea el diseño. El problema de CargaSur es que hoy se comprometen despachos sin verificar la capacidad
real:camiones sobrecargados o subutilizados.

**Factor elegido: impacto operacional y de seguridad vial de la sobrecarga.** Un camión por sobre su capacidad legal
implica riesgo de accidente (frenado, estabilidad), multas y detención en fiscalización, y desgaste acelerado; uno
subutilizado implica costo por viaje vacío. El alto volumen de consultas del enunciado es un requisito de rendimiento,
no el factor de contexto.

## Decisiones de diseño que se derivan del factor

| Decisión                                                                               | Por qué se deriva del factor                                                                                                   |
|----------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------|
| **Invariante Σ cargas ≤ capacidad garantizada en Flota con bloqueo de fila** (ADR-006) | Un sobrecupo es un daño físico y legal, no un dato inconsistente; no puede depender de que dos solicitudes no coincidan.       |
| **Falla cerrada:** si Flota no responde, no se registra el despacho (503)              | Es preferible rechazar un despacho (costo operativo menor) que comprometer uno sin verificar capacidad (riesgo de sobrecarga). |
| **La caché es consultiva; la reserva nunca se decide con ella** (ADR-006)              | Una lectura desactualizada solo cambia lo que se muestra; no puede producir sobrecupo.                                         |
| **Revertir libera exactamente la carga del despacho** (`loadId` único)                 | Liberar una carga ajena dejaría capacidad falsa disponible y llevaría a sobrecarga posterior.                                  |
| **Compensación si falla el guardado local tras reservar**                              | Evita reservar capacidad que ningún despacho usa (subutilización) y deja un registro consistente.                              |
| **409 (no reintentar) distinto de 503 (reintentar)**                                   | Evita que el cliente reintente ciegamente contra un camión lleno o abandone un despacho válido (ADR-004).                      |
| **Roles con mínimo privilegio y gRPC no publicado** (ADR-005)                          | Reservar o liberar capacidad cambia la operación real; solo un operador autenticado puede hacerlo.                             |

## Cómo se demuestra

La fase F de `experimento.py` lanza 20 solicitudes simultáneas de 100 kg sobre un camión con 800 kg libres y verifica
que se acepten exactamente 8, que nunca se exceda la capacidad, que los `loadId` sean únicos y que el resto
responda 409. El resultado queda en `docs/experimento/resumen.md`.

## Costo aceptado

Falla cerrada significa menor disponibilidad de la API cuando Flota falla (ADR-004); se acepta a cambio de no poner en
riesgo la seguridad operativa.

## Consecuencias

Toda funcionalidad nueva que cambie la capacidad (reservas parciales, cambio de camión) debe pasar por Flota bajo el
mismo bloqueo.
