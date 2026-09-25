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
