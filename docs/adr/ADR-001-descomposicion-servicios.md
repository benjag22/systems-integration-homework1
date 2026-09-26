# ADR-001 - Dos servicios independientes en vez de un monolito

**Estado:** Aceptada

## Contexto

CargaSur tiene dos dominios con ritmos de cambio y dueños distintos, Flota (camiones, rutas, capacidad) y Despachos
(clientes, órdenes). Hoy no se hablan entre sí y eso causa sobrecarga o baja utilización de camiones. Hay que decidir si
se integran en un único sistema o se mantienen como servicios separados que colaboran.

## Alternativas consideradas

- **Opción A - Monolito único:** Une ambos dominios en una sola base de código y una sola base de datos. Más simple de
  desplegar, pero acopla el ciclo de vida de Flota (equipo que ya la mantiene activamente) al de Despachos, y cualquier
  cambio de esquema de uno arriesga al otro.
- **Opción B - Dos servicios, un bounded context cada uno:** Flota conserva la verdad sobre camiones/capacidad, mientras
  que Despachos conserva clientes/órdenes y solo consulta a Flota. Requiere definir un contrato entre ambos y aceptar
  redundancia mínima de datos (Despachos guarda `truckId`, no la entidad completa del camión).
- **Opción C - Microservicios finos (uno por entidad):** Separar también las rutas, cargas y clientes en servicios
  propios. Fragmenta demasiado un dominio que no lo justifica en este alcance y multiplica la complejidad operativa.

## Decisión

Opción B: dos servicios, uno por bounded context (Flota y Despachos), cada uno con su propia base de datos.

## Justificación

La frontera coincide con quién es dueño de la verdad de cada dato, donde Flota es la única fuente de capacidad
disponible, mientras que Despachos es la única fuente de qué se comprometió. Esto permite que el equipo de Flota siga
evolucionando su sistema sin coordinar despliegues con Despachos, siempre que respete el contrato (`.proto`). La opción
C se descarta porque el dominio de Flota (camión + ruta + carga) cambia y se consulta como una unidad, por lo que
partirlo no reduce acoplamiento, solo agrega saltos de red.

## Costo aceptado

Despachos duplica a propósito `truckId` sin poder validar contra Flota más que en el momento de la reserva (no hay
integridad referencial entre bases). Si Flota da de baja un camión con despachos históricos, esa inconsistencia debe
tolerarse o resolverse a nivel de negocio, no al nivel de base de datos.

## Consecuencias

Todo cambio de capacidad debe pasar por Flota. Si en el futuro se agregan más consumidores de Flota (por ejemplo,
facturación), el mismo contrato gRPC los sirve sin tocar Despachos.
