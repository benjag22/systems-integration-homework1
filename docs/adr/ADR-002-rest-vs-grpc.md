# ADR-002 - REST para el lado público, gRPC para la comunicación interna

**Estado:** Aceptada

## Contexto

Despachos expone una API para personal de la organización y un futuro portal web (consumidores externos, variados, no
controlados por el equipo). Al mismo tiempo, Despachos necesita consultar disponibilidad en Flota con altísima
frecuencia (una llamada por cada intento de despacho) y de forma interna, entre dos servicios que sí controlamos.

## Alternativas consideradas

- **Opción A - REST/JSON en ambos lados:** Uniforme y fácil de debug con curl o el navegador, pero cada mensaje repite
  las claves del JSON como texto y no tiene un contrato binario fuerte, pues un typo en un campo solo se detecta en
  tiempo de ejecución.
- **Opción B - gRPC en ambos lados:** Máximo rendimiento y tipado en ambos lados, pero exige que cualquier cliente
  externo (portal web, personal no técnico probando con curl) utilice HTTP/2 + protobuf, lo que complica el lado público
  sin necesidad.
- **Opción C - REST hacia afuera, gRPC hacia adentro:** Cada protocolo donde su costo se justifica.

## Decisión

Opción C. La API pública de Despachos es REST/JSON versionada, mientras que la comunicación Despachos hacia Flota es
gRPC sobre un contrato `.proto` compartido.

## Justificación

- **Interoperabilidad:** el exterior (portal web, Postman, personal) no interfiere con el stack. Internamente, ambos
  servicios son nuestros, así que podemos exigir protobuf.
- **Tipado y contrato:** gRPC obliga a declarar campos y tipos en`trucks.proto`, donde un cambio incompatible falla en
  compilación, no en ejecución/producción. REST solo tiene esa garantía si se valida contra OpenAPI.
- **Tamaño de mensaje y rendimiento:** protobuf serializa binario y sin nombres de campo repetidos, más compacto que el
  JSON equivalente. Como la consulta de disponibilidad ocurre en cada despacho, ese ahorro se repite en cada llamada.
- **Depuración:** se acepta que gRPC es más opaco a simple vista (no se inspecciona con curl), esto se mitiga
  documentando el `.proto` y con las pruebas de contrato.

## Costo aceptado

Se deben generar stubs y mantener el `.proto` sincronizado en dos proyectos (Despachos y Flota), dado que un
desalineamiento entre ambas copias del contrato rompe la integración sin que el compilador lo avise, porque cada lado lo
compila por separado. Se mitiga versionando el mismo archivo en ambos repos y con `TruckProtoContractTest` de Flota.

## Consecuencias

Cualquier consumidor futuro de Flota que no sea otro backend (por ejemplo, un dashboard de solo lectura en el navegador)
necesitará un gateway REST-a-gRPC o gRPC-Web, este no puede hablarle directo.
