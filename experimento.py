"""Experimento de resiliencia y atomicidad de la integración Despachos <-> Flota.

Fases (cada una registra código HTTP, latencia y el código gRPC real que informa la API en `grpcStatus`):

  A. baseline_up        Flota arriba (esperado: 201).
  B. fleet_stopped      `docker compose stop fleet-api` (esperado: 503 + grpcStatus UNAVAILABLE, falla inmediata).
  C. recovery_stop      `docker compose start fleet-api`; se sondea hasta el primer 201 (tiempo de recuperación).
  D. fleet_paused       `docker compose pause fleet-api` (esperado: 503 + grpcStatus DEADLINE_EXCEEDED a ~2 s).
  E. recovery_pause     `docker compose unpause fleet-api`; se sondea hasta el primer 201.
  F. concurrency        N solicitudes simultáneas sobre un camión con capacidad limitada. Invariantes:
                        aceptados * peso <= disponible inicial, disponibilidad final consistente,
                        loadId únicos y el resto de las respuestas 409 (nunca 503).

Salidas (versionadas en docs/experimento/): resultados_crudos.csv, resumen.md, latencia.png.

Uso:
    uv sync && uv run experimento.py                 # reinicia el stack completo (down -v, up --build)
    uv run experimento.py --no-compose-reset         # usa el stack ya levantado (no hace down -v ni up)
    uv run experimento.py --plot-from-csv docs/experimento/resultados_crudos.csv
"""
import argparse
import base64
import csv
import json
import os
import statistics
import subprocess
import threading
import time
import urllib.error
import urllib.request
import uuid
from concurrent.futures import ThreadPoolExecutor
from typing import Any

DEFAULT_BASE_URL = "http://localhost:8080/v1"
OUT_DIR = os.path.join("docs", "experimento")
PHASES_LATENCY = (
    ("baseline_up", "Flota arriba", "tab:blue"),
    ("fleet_stopped", "Flota detenida (stop)", "tab:red"),
    ("fleet_paused", "Flota pausada (pause)", "tab:orange"),
)
CSV_FIELDS = ["phase", "trial", "idempotency_key", "status_code", "latency_ms", "grpc_status", "retry_after", "note"]


# ----------------------------------------------------------------------------- HTTP

def auth_header(user: str, pw: str) -> dict[str, str]:
    token = base64.b64encode(f"{user}:{pw}".encode()).decode()
    return {"Authorization": f"Basic {token}"}


def http_call(
    method: str, url: str, headers: dict[str, str], body: dict[str, Any] | None = None,
    timeout: int = 15,
) -> dict[str, Any]:
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(url, data=data, method=method)
    for k, v in headers.items():
        req.add_header(k, v)
    if data is not None:
        req.add_header("Content-Type", "application/json")

    start = time.perf_counter()
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            ms = (time.perf_counter() - start) * 1000
            raw = resp.read().decode() or "{}"
            return {"status": resp.status, "ms": ms, "payload": _json_or_empty(raw), "headers": dict(resp.headers)}
    except urllib.error.HTTPError as e:
        ms = (time.perf_counter() - start) * 1000
        raw = e.read().decode() or "{}"
        return {"status": e.code, "ms": ms, "payload": _json_or_empty(raw), "headers": dict(e.headers or {})}
    except (urllib.error.URLError, TimeoutError, ConnectionError) as e:
        ms = (time.perf_counter() - start) * 1000
        return {"status": None, "ms": ms, "payload": {"error": str(e)}, "headers": {}}


def _json_or_empty(raw: str) -> dict[str, Any]:
    try:
        value = json.loads(raw)
        return value if isinstance(value, dict) else {"value": value}
    except json.JSONDecodeError:
        return {}


class Ctx:
    def __init__(self, base_url: str, auth: dict[str, str], client_id: int = 0):
        self.base_url = base_url
        self.auth = auth
        self.client_id = client_id


def ensure_client(ctx: Ctx) -> int:
    email = f"exp-{uuid.uuid4().hex[:10]}@test.com"
    r = http_call(
        "POST", f"{ctx.base_url}/clientes", ctx.auth,
        {"name": "cliente experimento", "email": email, "phone": None},
    )
    if r["status"] != 201:
        raise SystemExit(f"no se pudo crear cliente de prueba: {r['status']} {r['payload']}")
    return r["payload"]["id"]


def post_shipment(ctx: Ctx, truck_id: int, weight_kg: int, note: str) -> dict[str, Any]:
    key = str(uuid.uuid4())
    body = {"clientId": ctx.client_id, "truckId": truck_id, "weightKg": weight_kg, "detail": note}
    r = http_call("POST", f"{ctx.base_url}/despachos", {**ctx.auth, "Idempotency-Key": key}, body)
    r["key"] = key
    return r


def revert(ctx: Ctx, shipment_id: int) -> None:
    http_call("POST", f"{ctx.base_url}/despachos/{shipment_id}/revertir", ctx.auth)


def availability(ctx: Ctx, truck_id: int) -> dict[str, Any]:
    r = http_call("GET", f"{ctx.base_url}/camiones/{truck_id}/disponibilidad", ctx.auth)
    if r["status"] != 200:
        raise SystemExit(f"no se pudo consultar la disponibilidad del camión {truck_id}: {r['status']} {r['payload']}")
    return r["payload"]


def make_row(phase: str, trial: int, r: dict[str, Any], note: str = "") -> dict[str, Any]:
    return {
        "phase": phase,
        "trial": trial,
        "idempotency_key": r.get("key", ""),
        "status_code": r["status"],
        "latency_ms": round(r["ms"], 2),
        "grpc_status": r["payload"].get("grpcStatus", ""),
        "retry_after": r["headers"].get("Retry-After", ""),
        "note": note,
    }


# ----------------------------------------------------------------------------- docker compose

def compose(args: argparse.Namespace, *cmd: str) -> None:
    full = [*args.compose_cmd.split(), *cmd]
    print("$", " ".join(full), flush=True)
    subprocess.run(full, check=True)


def wait_for_api(ctx: Ctx, timeout: int = 240) -> None:
    deadline = time.time() + timeout
    while time.time() < deadline:
        r = http_call("GET", f"{ctx.base_url}/clientes", ctx.auth, timeout=5)
        if r["status"] == 200:
            return
        time.sleep(2)
    raise SystemExit("dispatch-api no respondió a tiempo")


def wait_for_fleet(ctx: Ctx, truck_id: int, timeout: int = 240) -> None:
    """Espera a que la integración funcione de punta a punta (Flota lista y Despachos conectado)."""
    deadline = time.time() + timeout
    while time.time() < deadline:
        r = http_call("GET", f"{ctx.base_url}/camiones/{truck_id}/disponibilidad", ctx.auth, timeout=5)
        if r["status"] == 200:
            return
        time.sleep(2)
    raise SystemExit("Flota no quedó disponible a tiempo")


# ----------------------------------------------------------------------------- fases

def run_phase(ctx: Ctx, phase: str, trials: int, truck_id: int, weight: int) -> list[dict[str, Any]]:
    rows = []
    for i in range(1, trials + 1):
        r = post_shipment(ctx, truck_id, weight, f"experimento-{phase}-{i}")
        rows.append(make_row(phase, i, r))
        print(
            f"[{phase} {i:02d}/{trials}] status={r['status']} {r['ms']:.1f} ms "
            f"grpc={r['payload'].get('grpcStatus', '-')}", flush=True,
        )
        if r["status"] == 201:
            revert(ctx, r["payload"]["id"])
    return rows


def run_recovery(
    ctx: Ctx, phase: str, t0: float, truck_id: int, weight: int,
    timeout: int,
) -> tuple[list[dict[str, Any]], float | None]:
    """Sondea cada segundo hasta el primer 201. t0 = instante en que se ejecutó start/unpause."""
    rows: list[dict[str, Any]] = []
    attempt = 0
    while time.time() - t0 < timeout:
        attempt += 1
        r = post_shipment(ctx, truck_id, weight, f"experimento-{phase}-{attempt}")
        elapsed = time.time() - t0
        rows.append(make_row(phase, attempt, r, note=f"t={elapsed:.1f}s"))
        print(
            f"[{phase} {attempt:02d}] t={elapsed:.1f}s status={r['status']} "
            f"grpc={r['payload'].get('grpcStatus', '-')}", flush=True,
        )
        if r["status"] == 201:
            revert(ctx, r["payload"]["id"])
            return rows, elapsed
        time.sleep(1)
    return rows, None


def run_concurrency(ctx: Ctx, truck_id: int, weight: int, n: int) -> tuple[list[dict[str, Any]], dict[str, Any]]:
    before = availability(ctx, truck_id)
    free_before = before["availableCapacityKg"]
    barrier = threading.Barrier(n)

    def worker(i: int) -> dict[str, Any]:
        barrier.wait()
        r = post_shipment(ctx, truck_id, weight, f"experimento-concurrencia-{i}")
        r["trial"] = i
        return r

    with ThreadPoolExecutor(max_workers=n) as pool:
        results = list(pool.map(worker, range(1, n + 1)))

    after = availability(ctx, truck_id)
    accepted = [r for r in results if r["status"] == 201]
    load_ids = [r["payload"].get("loadId") for r in accepted]
    codes = sorted({r["status"] for r in results}, key=lambda c: (c is None, c))
    expected_accepted = min(n, free_before // weight)

    checks = {
        "no_sobrecupo": len(accepted) * weight <= free_before,
        "disponibilidad_final_consistente": after["availableCapacityKg"] == free_before - len(accepted) * weight,
        "load_id_unicos": len(set(load_ids)) == len(load_ids) and None not in load_ids,
        "solo_201_y_409": set(codes) <= {201, 409},
        "aceptados_esperados": len(accepted) == expected_accepted,
    }
    summary = {
        "truck_id": truck_id, "weight": weight, "n": n,
        "free_before": free_before, "free_after": after["availableCapacityKg"],
        "accepted": len(accepted), "expected_accepted": expected_accepted,
        "codes": codes, "checks": checks,
    }
    rows = [make_row("concurrency", r["trial"], r) for r in results]
    for r in accepted:
        revert(ctx, r["payload"]["id"])
    print(
        f"[concurrency] aceptados={len(accepted)} esperados={expected_accepted} códigos={codes} checks={checks}",
        flush=True,
    )
    return rows, summary


# ----------------------------------------------------------------------------- resultados

def pct(values: list[float], q: float) -> float:
    ordered = sorted(values)
    idx = min(len(ordered) - 1, max(0, int(round(q * (len(ordered) - 1)))))
    return ordered[idx]


def count_by(rows: list[dict[str, Any]], field: str) -> str:
    counts: dict[str, int] = {}
    for r in rows:
        counts[str(r[field]) or "-"] = counts.get(str(r[field]) or "-", 0) + 1
    return ", ".join(f"{k}: {v}" for k, v in sorted(counts.items()))


def latency_table(rows: list[dict[str, Any]]) -> str:
    lines = [
        "| Fase | n | Código HTTP | grpcStatus | Media (ms) | Mediana (ms) | Desv. est. (ms) | P95 (ms) | Mín–Máx ("
        "ms) |",
        "|---|---|---|---|---|---|---|---|---|",
    ]
    for phase, label, _ in PHASES_LATENCY:
        pr = [r for r in rows if r["phase"] == phase]
        if not pr:
            continue
        lat = [float(r["latency_ms"]) for r in pr]
        stdev = statistics.stdev(lat) if len(lat) > 1 else 0.0
        lines.append(
            f"| {label} | {len(pr)} | {count_by(pr, 'status_code')} | {count_by(pr, 'grpc_status')} | "
            f"{statistics.mean(lat):.1f} | {statistics.median(lat):.1f} | {stdev:.1f} | {pct(lat, 0.95):.1f} | "
            f"{min(lat):.1f}–{max(lat):.1f} |",
        )
    return "\n".join(lines)


def recovery_table(recoveries: dict[str, float | None]) -> str:
    lines = ["| Escenario | Tiempo hasta el primer 201 |", "|---|---|"]
    names = {"recovery_stop": "start tras stop (arranque completo de Flota)", "recovery_pause": "unpause tras pause"}
    for key, label in names.items():
        if key in recoveries:
            value = recoveries[key]
            lines.append(f"| {label} | {'no se recuperó en el tiempo máximo' if value is None else f'{value:.1f} s'} |")
    return "\n".join(lines)


def concurrency_section(s: dict[str, Any]) -> str:
    lines = [
        f"Camión {s['truck_id']}, {s['n']} solicitudes simultáneas de {s['weight']} kg. "
        f"Disponible antes: {s['free_before']} kg; después: {s['free_after']} kg. "
        f"Aceptados: {s['accepted']} (esperados: {s['expected_accepted']}). Códigos HTTP: {s['codes']}.",
        "",
        "| Invariante | Resultado |",
        "|---|---|",
    ]
    names = {
        "no_sobrecupo": "Σ aceptados × peso ≤ disponible inicial (nunca hay sobrecupo)",
        "disponibilidad_final_consistente": "disponible final = disponible inicial − Σ aceptados × peso",
        "load_id_unicos": "cada despacho aceptado tiene un loadId distinto",
        "solo_201_y_409": "las respuestas son solo 201 o 409 (la falta de capacidad nunca es 503)",
        "aceptados_esperados": "aceptados = floor(disponible / peso)",
    }
    for key, label in names.items():
        lines.append(f"| {label} | {'CUMPLE' if s['checks'][key] else 'FALLA'} |")
    return "\n".join(lines)


def write_outputs(
    rows: list[dict[str, Any]],
    recoveries: dict[str, float | None],
    conc: dict[str, Any] | None,
    out_dir: str,
) -> None:
    os.makedirs(out_dir, exist_ok=True)
    csv_path = os.path.join(out_dir, "resultados_crudos.csv")
    with open(csv_path, "w", newline="") as f:
        w = csv.DictWriter(f, fieldnames=CSV_FIELDS)
        w.writeheader()
        for r in rows:
            w.writerow({k: r.get(k, "") for k in CSV_FIELDS})
    print(f"datos guardados en {csv_path}")

    sections = [
        "# Resultados del experimento",
        "",
        "Generado por `experimento.py`; los datos crudos están en `resultados_crudos.csv` y el gráfico en "
        "`latencia.png`.",
        "",
        "## Latencia y causa de la falla por fase",
        "",
        latency_table(rows),
        "",
    ]
    if recoveries:
        sections += ["## Recuperación", "", recovery_table(recoveries), ""]
    if conc:
        sections += ["## Concurrencia sobre la capacidad", "", concurrency_section(conc), ""]
    with open(os.path.join(out_dir, "resumen.md"), "w") as f:
        f.write("\n".join(sections))
    print(f"resumen guardado en {os.path.join(out_dir, 'resumen.md')}")
    plot_latencies(rows, os.path.join(out_dir, "latencia.png"))


def plot_latencies(rows: list[dict[str, Any]], out_path: str) -> None:
    try:
        import matplotlib

        matplotlib.use("Agg")
        import matplotlib.pyplot as plt
    except ImportError:
        print("[plot] matplotlib no está instalado")
        return

    fig, ax = plt.subplots(figsize=(9, 5))
    for phase, label, color in PHASES_LATENCY:
        pr = [r for r in rows if r["phase"] == phase]
        if not pr:
            continue
        ax.plot([r["trial"] for r in pr], [float(r["latency_ms"]) for r in pr], marker="o", label=label, color=color)
    ax.axhline(2000, color="gray", linestyle="--", linewidth=1, label="deadline gRPC (2 s)")
    ax.set_yscale("log")
    ax.set_xlabel("trial")
    ax.set_ylabel("latencia (ms, escala log)")
    ax.set_title("Latencia POST /v1/despachos por trial según el estado de Flota")
    ax.legend()
    ax.grid(True, alpha=0.3, which="both")
    fig.tight_layout()
    fig.savefig(out_path, dpi=150)
    plt.close(fig)
    print(f"[plot] guardado en {out_path}")


def load_rows_from_csv(path: str) -> list[dict[str, Any]]:
    rows: list[dict[str, Any]] = []
    with open(path, newline="") as f:
        for r in csv.DictReader(f):
            r["trial"] = int(r["trial"])
            r["latency_ms"] = float(r["latency_ms"])
            rows.append(r)
    return rows


# ----------------------------------------------------------------------------- main

def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--base-url", default=DEFAULT_BASE_URL)
    ap.add_argument("--user", default="demo")
    ap.add_argument("--password", default="demo123")
    ap.add_argument("--truck-id", type=int, default=3, help="camión de las fases de latencia (ijkl-56, 3500 kg libres)")
    ap.add_argument("--weight-kg", type=int, default=1)
    ap.add_argument("--trials", type=int, default=20)
    ap.add_argument(
        "--conc-truck-id",
        type=int,
        default=2,
        help="camión de la fase de concurrencia (efgh-34, 800 kg libres)",
    )
    ap.add_argument("--conc-weight", type=int, default=100)
    ap.add_argument("--conc-requests", type=int, default=20)
    ap.add_argument("--recovery-timeout", type=int, default=180)
    ap.add_argument("--compose-cmd", default="docker compose")
    ap.add_argument("--no-compose-reset", action="store_true", help="no hacer down -v ni up --build")
    ap.add_argument("--out-dir", default=OUT_DIR)
    ap.add_argument("--plot-from-csv", metavar="PATH", help="solo regenera el gráfico desde un CSV existente")
    args = ap.parse_args()

    if args.plot_from_csv:
        plot_latencies(load_rows_from_csv(args.plot_from_csv), os.path.join(args.out_dir, "latencia.png"))
        return

    ctx = Ctx(args.base_url, auth_header(args.user, args.password))

    if not args.no_compose_reset:
        compose(args, "down", "-v")
        compose(args, "up", "-d", "--build")
    wait_for_api(ctx)
    wait_for_fleet(ctx, args.truck_id)
    ctx.client_id = ensure_client(ctx)
    print(f"clientId={ctx.client_id}")

    rows: list[dict[str, Any]] = []
    recoveries: dict[str, float | None] = {}

    print("\n== calentamiento (descartado de las estadísticas)")
    warm = post_shipment(ctx, args.truck_id, args.weight_kg, "experimento-warmup")
    rows.append(make_row("warmup", 1, warm))
    if warm["status"] == 201:
        revert(ctx, warm["payload"]["id"])

    print("\n== A. baseline: Flota arriba")
    rows += run_phase(ctx, "baseline_up", args.trials, args.truck_id, args.weight_kg)

    print("\n== B. Flota detenida (stop)")
    compose(args, "stop", "fleet-api")
    time.sleep(2)
    rows += run_phase(ctx, "fleet_stopped", args.trials, args.truck_id, args.weight_kg)

    print("\n== C. recuperación tras start")
    t0 = time.time()
    compose(args, "start", "fleet-api")
    rec_rows, recoveries["recovery_stop"] = run_recovery(
        ctx, "recovery_stop", t0, args.truck_id, args.weight_kg, args.recovery_timeout,
    )
    rows += rec_rows

    print("\n== D. Flota pausada (pause)")
    compose(args, "pause", "fleet-api")
    rows += run_phase(ctx, "fleet_paused", args.trials, args.truck_id, args.weight_kg)

    print("\n== E. recuperación tras unpause")
    t0 = time.time()
    compose(args, "unpause", "fleet-api")
    rec_rows, recoveries["recovery_pause"] = run_recovery(
        ctx, "recovery_pause", t0, args.truck_id, args.weight_kg, args.recovery_timeout,
    )
    rows += rec_rows

    print("\n== F. concurrencia sobre la capacidad")
    conc_rows, conc = run_concurrency(ctx, args.conc_truck_id, args.conc_weight, args.conc_requests)
    rows += conc_rows

    write_outputs(rows, recoveries, conc, args.out_dir)


if __name__ == "__main__":
    main()
