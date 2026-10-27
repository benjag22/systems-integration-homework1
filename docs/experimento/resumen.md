# Resultados del experimento

Generado por `experimento.py`; los datos crudos están en `resultados_crudos.csv` y el gráfico en `latencia.png`.

## Latencia y causa de la falla por fase

| Fase | n | Código HTTP | grpcStatus | Media (ms) | Mediana (ms) | Desv. est. (ms) | P95 (ms) | Mín–Máx (ms) |
|---|---|---|---|---|---|---|---|---|
| Flota arriba | 20 | 201: 20 | -: 20 | 72.5 | 72.4 | 2.0 | 74.9 | 68.6–77.2 |
| Flota detenida (stop) | 20 | 503: 20 | DEADLINE_EXCEEDED: 14, UNAVAILABLE: 6 | 1517.9 | 2057.8 | 884.0 | 2061.9 | 56.3–2079.4 |
| Flota pausada (pause) | 20 | 503: 20 | DEADLINE_EXCEEDED: 20 | 2059.2 | 2058.9 | 1.4 | 2061.9 | 2057.0–2062.1 |

## Recuperación

| Escenario | Tiempo hasta el primer 201 |
|---|---|
| start tras stop (arranque completo de Flota) | 11.8 s |
| unpause tras pause | 0.1 s |

## Concurrencia sobre la capacidad

Camión 2, 20 solicitudes simultáneas de 100 kg. Disponible antes: 800 kg; después: 0 kg. Aceptados: 8 (esperados: 8). Códigos HTTP: [201, 409].

| Invariante | Resultado |
|---|---|
| Σ aceptados × peso ≤ disponible inicial (nunca hay sobrecupo) | CUMPLE |
| disponible final = disponible inicial − Σ aceptados × peso | CUMPLE |
| cada despacho aceptado tiene un loadId distinto | CUMPLE |
| las respuestas son solo 201 o 409 (la falta de capacidad nunca es 503) | CUMPLE |
| aceptados = floor(disponible / peso) | CUMPLE |
