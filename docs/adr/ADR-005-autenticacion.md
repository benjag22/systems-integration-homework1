# ADR-005 - Autenticación y autorización de la API REST

**Estado:** Aceptada

## Contexto

La API de Despachos la consume personal de la organización y, a futuro, un portal web. Modifica el estado de la flota
(reserva y libera capacidad), por lo que debe identificar al llamador y separar quién puede leer de quién puede
escribir. El enunciado exige autenticación y pide justificar la elección.

## Alternativas consideradas

- **API Key en cabecera:** simple, pero identifica una aplicación, no un usuario; no distingue roles sin infraestructura
  adicional.
- **HTTP Basic sobre HTTPS:** estándar (RFC 7617), sin estado, compatible con curl, Postman y Swagger UI, y soportado de
  fábrica por Spring Security.
- **Bearer/JWT (OAuth2):** adecuado para producción (credenciales de corta vida, revocación, SSO), pero exige un
  servidor de identidad que este alcance no tiene.
- **mTLS:** fuerte para servicio a servicio, poco práctico para personas y un portal web.

## Decisión

HTTP Basic con dos usuarios en memoria y roles, configurables por variable de entorno:

- `OPERATOR` (`AUTH_USERNAME`/`AUTH_PASSWORD`, por defecto `demo`/`demo123`): lectura y escritura.
- `VIEWER` (`AUTH_VIEWER_USERNAME`/`AUTH_VIEWER_PASSWORD`, por defecto `viewer`/`viewer123`): solo `GET`.

Respuestas: **401** sin credenciales o con credenciales erróneas (con `WWW-Authenticate: Basic`) y **403** cuando un
`VIEWER` intenta escribir; ambas como `application/problem+json`. Las contraseñas se almacenan con
`DelegatingPasswordEncoder` (bcrypt).

## Justificación

Es la opción más simple que cumple lo pedido (identificar y autorizar por rol) sin añadir infraestructura. Los dos roles
dan sentido al 403 y aplican mínimo privilegio: quien solo consulta no puede reservar capacidad de camiones.

## Costo aceptado

Basic envía las credenciales en cada solicitud y solo es seguro sobre TLS; el entorno del encargo corre en HTTP local.
No hay revocación ni caducidad, y los usuarios son estáticos. Es una limitación consciente de este alcance.

## Consecuencias

En producción: terminar TLS delante de la API y migrar a Bearer/JWT emitido por un proveedor de identidad. Como la
autorización ya usa roles, el cambio se limita a la cadena de filtros de `SecurityConfig`. El puerto gRPC de Flota no se
publica al host (no tiene autenticación y solo debe ser alcanzable desde la red interna).
