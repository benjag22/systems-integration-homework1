# ADR-003 - Versionado, evolución y comunicación de cambios de los contratos

**Estado:** Aceptada (revisada: `reserved`, avisos concretos y fuente única del `.proto`)

## Contexto

Los dos contratos (`openapi.yaml` para REST, `proto/trucks.proto` para gRPC) van a cambiar con el tiempo (nuevos campos,
nuevas reglas de negocio). Hay que definir de antemano qué cambios se consideran seguros, cómo se enteran los
consumidores y cómo se evita que el contrato y el código diverjan, en vez de decidirlo caso a caso.

## Alternativas consideradas

- **Opción A - Todo aditivo para siempre:** máxima compatibilidad, pero acumula campos obsoletos indefinidamente.
- **Opción B - Reglas explícitas de compatibilidad + versión nueva solo cuando se rompe:** lo aditivo no requiere
  versión nueva; lo incompatible exige una versión nueva que convive con la anterior (deprecación y luego eliminación).
- **Opción C - Versionar en cada cambio:** simple de anunciar, pero fuerza a todos los consumidores a migrar aunque el
  cambio no los afecte.
- **Fuente del `.proto`:** (1) una copia por proyecto, sincronizadas a mano; (2) un único archivo referenciado por todos
  los builds.

## Decisión

Opción B, con estas reglas:

- **REST:** versión en la URL (`/v1`). Son compatibles: agregar un campo opcional, un endpoint o un código de error
  documentado (p. ej. `GET /camiones/{id}/disponibilidad` y el campo `grpcStatus` de los errores, ambos en 1.1.0, sin
  `/v2`). Son incompatibles: quitar o renombrar un campo, cambiar su tipo o volver obligatorio algo opcional; exigen
  `/v2` conviviendo con `/v1`.
- **gRPC/protobuf:** agregar un campo con un número nuevo es compatible (p. ej. `Truck.created_load = 5`, agregado en
  1.1 sin versión nueva). Cambiar el tipo o reutilizar un número no es compatible y exige un mensaje o servicio nuevo
  (`TruckServiceV2`). **Al eliminar un campo se declara `reserved <número>; reserved "<nombre>";`** para que no se
  reutilice por error.
- **Cómo se enteran los consumidores:** (1) `docs/CHANGELOG-contratos.md` registra cada versión de ambos contratos; (2)
  lo que se retira se marca antes con `deprecated: true` en OpenAPI (y `[deprecated = true]` en protobuf) durante, al
  menos, una versión; (3) en `/v1` las respuestas de una operación en retiro llevan las cabeceras `Deprecation` y
  `Sunset` (RFC 8594) con la fecha de eliminación; (4) `info.version` de OpenAPI sube en cada cambio.
- **Fuente única del `.proto`:** `proto/trucks.proto` en la raíz. Flota (`srcDir("../proto")`), Despachos
  (`protoSourceRoot`) y el cliente .NET lo compilan directamente; los Dockerfiles copian esa carpeta. Ya no existen
  copias que puedan desalinearse.
- **Contrato y código no divergen:** `OpenApiContractTest` (Despachos) valida las respuestas reales de los controladores
  contra `openapi.yaml`, y `TruckProtoContractTest` (Flota) valida el descriptor generado. El contrato es la fuente de
  verdad: si el código difiere, se corrige el código (p. ej. el correo duplicado responde 409 como declara el contrato).
- **OpenAPI 3.0.3:** se usa 3.0 con `nullable: true` porque el parser del test de contrato (swagger-core) no interpreta
  la sintaxis de 3.1 (`type: [string, 'null']`).

## Justificación

La distinción aditivo / incompatible es la que protobuf ya modela con sus números de campo; seguirla evita reinventar
reglas. Para REST, `/v1` en la ruta es el mecanismo más simple de anunciar una ruptura sin negociación de contenido. Una
sola fuente del `.proto` elimina por construcción el riesgo que ADR-002 aceptaba como costo.

## Costo aceptado

Mantener dos versiones vivas en paralelo (`/v1` y `/v2`, o dos RPC) durante la migración, y que el cambio de un `.proto`
compartido obligue a recompilar todos los consumidores del repositorio.

## Consecuencias

Todo cambio incompatible se declara primero en `proto/trucks.proto` u `openapi.yaml`, se registra en el changelog y se
anuncia con `deprecated`/`Sunset` antes de retirar la versión vieja.
