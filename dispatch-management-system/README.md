## Ejecución

### Requisitos
- Docker y Docker Compose instalados.

### Levantar el sistema

1. (Opcional) Copiar el archivo de variables de entorno:
```bash
   cp .env.example .env
```
Si se omite este paso, el sistema arranca igual con los valores por defecto
definidos en `docker-compose.yml`.

2. Levantar todos los servicios:
```bash
   docker compose up --build
```

3. La API queda disponible en `http://localhost:8080/v1`.
    - Documentación Swagger UI: `http://localhost:8080/v1/docs`
    - Spec OpenAPI: `http://localhost:8080/v1/api-docs`
   La API usa autenticación HTTP Basic. Para desarrollo, las credenciales predeterminadas son `demo` / `demo123`.

   Para comprobarla desde PowerShell:
```powershell
   curl.exe -i http://localhost:8080/v1/clientes
   curl.exe -i -u demo:demo123 http://localhost:8080/v1/clientes
```
   La primera llamada debe responder `401 Unauthorized`; la segunda, `200 OK`.

   Puedes cambiar las credenciales en `.env` usando `AUTH_USERNAME` y `AUTH_PASSWORD`.

### Comprobar idempotencia al crear despachos

El `POST /v1/despachos` requiere el encabezado `Idempotency-Key`. Reenvía la misma clave y el mismo JSON para recuperar el despacho original sin reservar capacidad ni crear otro registro. Si reutilizas esa clave con datos distintos, la API responde `409 Conflict`.

En PowerShell, usa una clave y un JSON iguales en las dos llamadas:
```powershell
$body = '{"clientId":1,"truckId":1,"weightKg":100,"detail":"Prueba de idempotencia"}'
curl.exe -i -u demo:demo123 -H "Content-Type: application/json" -H "Idempotency-Key: prueba-despacho-001" -d $body http://localhost:8080/v1/despachos
curl.exe -i -u demo:demo123 -H "Content-Type: application/json" -H "Idempotency-Key: prueba-despacho-001" -d $body http://localhost:8080/v1/despachos
```
Ambas respuestas deben incluir el mismo `id`. Para comprobar que no se duplicó, consulta `GET /v1/despachos` y verifica que solo haya un despacho con ese `id`.

La tabla de claves idempotentes se crea automáticamente al iniciar la API, incluso si ya existe el volumen de PostgreSQL.

### Enlaces HATEOAS de los despachos

Las respuestas de despacho incluyen `_links.self` para consultar el recurso y `_links.collection` para volver a la lista. Mientras el estado sea `REGISTERED`, también aparece `_links.revert` con la ruta para revertirlo. Después de revertirlo, el enlace `revert` deja de aparecer.

### Ejecutar pruebas de contrato

Las pruebas de REST cargan el `openapi.yaml`, ejercitan las rutas mediante MockMvc y comparan estado, request y JSON de respuesta con los schemas declarados. Desde esta carpeta:
```powershell
.\mvnw.cmd -Dtest=OpenApiContractTest test
```
La prueba del contrato gRPC valida los descriptores generados desde `trucks.proto` contra los RPC, mensajes y campos implementados. Desde `truck-fleet-system`:
```powershell
cd ..\truck-fleet-system
.\gradlew.bat test --tests com.example.truckfleetsystem.grpc.TruckProtoContractTest
```

4. Para detener y limpiar:
```bash
   docker compose down -v
```

### Servicios levantados

| Servicio | Puerto | Descripción |
|---|---|---|
| `dispatch-api` | 8080 | API REST del sistema de Despachos |
| `dispatch-postgres` | 5433 (host) | Base de datos propia de Despachos |

### Uso de asistentes de IA
