# ADR-003 - Versionado y evolución de los contratos

**Estado:** Aceptada

## Contexto

Los dos contratos (`openapi.yaml` para REST, `trucks.proto` para gRPC) van a cambiar con el tiempo (e.j. nuevos campos,
nuevas reglas de negocio). Hay que definir de antemano qué cambios se consideran seguros y cómo se enteran los
consumidores, en vez de decidirlo caso a caso.

## Alternativas consideradas

- **Opción A - Nunca romper nada, todo aditivo para siempre:** Máxima compatibilidad, pero acumula campos obsoletos
  indefinidamente y complica el contrato.
- **Opción B - Reglas explícitas de compatibilidad + versión nueva solo cuando se rompen:** Aditivo se permite sin
  versión nueva, donde lo incompatible exige una versión nueva y coexistencia temporal entre versiones (deprecación y
  luego eliminación).
- **Opción C - Versionar en cada cambio, sin distinguir aditivo de incompatible:** Simple de anunciar, pero fuerza a
  todos los consumidores a migrar aunque el cambio no los afecte.

## Decisión

Opción B, aplicada distinto según el protocolo:

- **REST:** versión en la URL (`/v1`). Agregar un campo opcional a un`schema` (por ejemplo, `phone` ya es opcional en
  `CreateClientRequest`) es compatible y no exige `/v2`. Quitar un campo, cambiar su tipo, o volver obligatorio un campo
  antes opcional, exige `/v2` conviviendo con `/v1`mientras haya consumidores del contrato viejo.
- **gRPC/protobuf:** agregar un campo nuevo con un número de tag nuevo es compatible (los clientes viejos lo ignoran.
  Ver `TruckAvailability` si mañana se agrega, por ejemplo, `estimated_delivery_days = 4`). Renumerar o reutilizar un
  tag existente, o cambiar el tipo de un campo, no es compatible y exige un nuevo mensaje o servicio (`TruckServiceV2`)
  en el mismo `.proto`.

## Justificación

Esta distinción (aditivo vs. incompatible) es exactamente la que protobuf ya modela con sus números de campo, así que
seguirla evita reinventar reglas. Para REST, `/v1` en la ruta es el mecanismo más simple de anunciar una ruptura sin
necesitar content negotiation, además de ser convención utilizada en la industria.

## Costo aceptado

Mantener dos versiones vivas en paralelo (`/v1` y `/v2`, o dos rpcs) tiene costo de mantenimiento mientras dura la
migración, esto se acepta a cambio de no romper consumidores sin aviso.

## Consecuencias

Todo cambio incompatible debe declararse primero en el `.proto` u`openapi.yaml` versionado en el repositorio, antes de
tocar el código, y notificarse a los consumidores conocidos (personal interno, portal web, Flota) con antelación a que
se retire la versión vieja.
