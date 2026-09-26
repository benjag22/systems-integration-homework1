import argparse
import base64
import csv
import json
import statistics
import subprocess
import time
import urllib.error
import urllib.request
import uuid
from typing import Any

DEFAULT_BASE_URL = "http://localhost:8080/v1"
DEFAULT_USER = "demo"
DEFAULT_PASS = "demo123"
DEFAULT_TRUCK_ID = 3
DEFAULT_TRIALS = 20
DEFAULT_WEIGHT_KG = 1


def auth_header(user: str, pw: str) -> dict[str, str]:
    token = base64.b64encode(f"{user}:{pw}".encode()).decode()
    return {"Authorization": f"Basic {token}"}


def http_call(
    method: str,
    url: str,
    headers: dict[str, str],
    body: dict[str, Any] | None = None,
    timeout: int = 10,
) -> tuple[int | None, float | int, dict[str, Any], dict[str, Any]]:
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(url, data=data, method=method)
    for k, v in headers.items():
        req.add_header(k, v)
    if data is not None:
        req.add_header("Content-Type", "application/json")

    start = time.perf_counter()
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            elapsed_ms = (time.perf_counter() - start) * 1000
            payload = json.loads(resp.read().decode() or "{}")
            return resp.status, elapsed_ms, payload, dict(resp.headers)
    except urllib.error.HTTPError as e:
        elapsed_ms = (time.perf_counter() - start) * 1000
        try:
            payload = json.loads(e.read().decode() or "{}")
        except Exception:
            payload = {}
        return e.code, elapsed_ms, payload, dict(e.headers or {})
    except urllib.error.URLError as e:
        elapsed_ms = (time.perf_counter() - start) * 1000
        return None, elapsed_ms, {"error": str(e.reason)}, {}


def ensure_client(base_url: str, auth: dict[str, str]) -> int:
    email = f"exp-{uuid.uuid4().hex[:10]}@test.com"
    status, _, payload, _ = http_call(
        "POST", f"{base_url}/clientes", auth,
        {"name": "cliente experimento", "email": email, "phone": None},
    )
    if status != 201:
        raise SystemExit(f"no se pudo crear cliente de prueba: {status} {payload}")
    return payload["id"]


def run_trial(
    base_url: str,
    auth: dict[str, str],
    client_id: int,
    truck_id: int,
    weight_kg: int,
    note: str,
) -> tuple[str, int | None, float | int, dict[str, Any], dict[str, Any]]:
    key = str(uuid.uuid4())
    body = {"clientId": client_id, "truckId": truck_id, "weightKg": weight_kg, "detail": note}
    headers = {**auth, "Idempotency-Key": key}
    status, elapsed_ms, payload, resp_headers = http_call("POST", f"{base_url}/despachos", headers, body)
    return key, status, elapsed_ms, payload, resp_headers


def revert(base_url: str, auth: dict[str, str], shipment_id: int) -> None:
    http_call("POST", f"{base_url}/despachos/{shipment_id}/revertir", auth)


def summarize(rows: list[dict[str, Any]], phase: str) -> dict[str, Any] | None:
    latencies = [r["latency_ms"] for r in rows if r["phase"] == phase]
    if not latencies:
        return None
    return {
        "phase": phase,
        "n": len(latencies),
        "mean_ms": statistics.mean(latencies),
        "stdev_ms": statistics.stdev(latencies) if len(latencies) > 1 else 0.0,
        "min_ms": min(latencies),
        "max_ms": max(latencies),
        "median_ms": statistics.median(latencies),
    }


def plot_latencies(rows: list[dict[str, Any]], out_path: str, skip_warmup: bool = True) -> None:
    try:
        import matplotlib
        matplotlib.use("Agg")
        import matplotlib.pyplot as plt
    except ImportError:
        print("[plot] matplotlib no está instalado")
        return

    fig, ax = plt.subplots(figsize=(9, 5))

    for phase, label, color in (
        ("baseline_up", "Flota arriba (201)", "tab:blue"),
        ("fleet_down", "Flota caída (503)", "tab:red"),
    ):
        phase_rows = [r for r in rows if r["phase"] == phase]
        if skip_warmup and phase == "baseline_up" and len(phase_rows) > 1:
            phase_rows = phase_rows[1:]
        if not phase_rows:
            continue
        trials = [r["trial"] for r in phase_rows]
        latencies = [r["latency_ms"] for r in phase_rows]
        ax.plot(trials, latencies, marker="o", label=label, color=color)

    ax.set_xlabel("trial")
    ax.set_ylabel("latencia (ms)")
    ax.set_title("Latencia POST /v1/despachos por trial: Flota arriba vs caída")
    ax.legend()
    ax.grid(True, alpha=0.3)
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


def wait_for_api(base_url: str, auth: dict[str, str], timeout: int = 120) -> None:
    deadline = time.time() + timeout
    time.sleep(10)
    while time.time() < deadline:
        try:
            status, _, _, _ = http_call("GET", f"{base_url}/clientes", auth)
        except Exception:
            status = None
        if status == 200:
            return
        time.sleep(2)
    raise SystemExit("dispatch-api no respondió a tiempo tras docker compose up")


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--base-url", default=DEFAULT_BASE_URL)
    ap.add_argument("--user", default=DEFAULT_USER)
    ap.add_argument("--password", default=DEFAULT_PASS)
    ap.add_argument("--truck-id", type=int, default=DEFAULT_TRUCK_ID)
    ap.add_argument("--trials", type=int, default=DEFAULT_TRIALS)
    ap.add_argument("--weight-kg", type=int, default=DEFAULT_WEIGHT_KG)
    ap.add_argument("--out", default="experiment.csv")
    ap.add_argument("--plot-out", default="latency_plot.png", help="ruta del gráfico PNG")
    ap.add_argument("--no-plot", action="store_true", help="no generar el gráfico")
    ap.add_argument("--plot-from-csv", metavar="PATH", help="solo lee PATH y genera el gráfico")
    args = ap.parse_args()

    if args.plot_from_csv:
        rows = load_rows_from_csv(args.plot_from_csv)
        plot_latencies(rows, args.plot_out)
        return

    print("docker compose down -v")
    subprocess.run(["docker", "compose", "down", "-v"], check=True)

    print("docker compose up -d --build")
    subprocess.run(["docker", "compose", "up", "-d", "--build"], check=True)

    auth = auth_header(args.user, args.password)
    rows = []

    print(f"esperando dispatch-api en {args.base_url}")
    wait_for_api(args.base_url, auth)

    print(f"creando cliente de prueba en {args.base_url}")
    client_id = ensure_client(args.base_url, auth)
    print(f"clientId={client_id}")

    print("\nfase A: baseline (fleet-api encendido)")
    for i in range(1, args.trials + 1):
        key, status, ms, payload, _ = run_trial(
            args.base_url, auth, client_id, args.truck_id, args.weight_kg,
            f"experimento-baseline-{i}",
        )
        rows.append(
            {
                "phase": "baseline_up",
                "trial": i,
                "idempotency_key": key,
                "status_code": status,
                "latency_ms": round(ms, 2),
            },
        )
        print(f"[baseline {i:02d}/{args.trials}] status={status} latency={ms:.1f}ms")
        if status == 201 and "id" in payload:
            revert(args.base_url, auth, payload["id"])

    print("\nfase B: falla (fleet-api apagada)")
    print("docker compose stop fleet-api")
    subprocess.run(["docker", "compose", "stop", "fleet-api"], check=True)
    time.sleep(2)
    for i in range(1, args.trials + 1):
        key, status, ms, payload, headers = run_trial(
            args.base_url, auth, client_id, args.truck_id, args.weight_kg,
            f"experimento-timeout-caida-{i}",
        )
        rows.append(
            {
                "phase": "fleet_down",
                "trial": i,
                "idempotency_key": key,
                "status_code": status,
                "latency_ms": round(ms, 2),
                "retry_after": headers.get("Retry-After", ""),
            },
        )
        print(
            f"[caida {i:02d}/{args.trials}] status={status} latency={ms:.1f}ms "
            f"retry-after={headers.get('Retry-After')}",
        )

    print("\ndocker compose start fleet-api")
    subprocess.run(["docker", "compose", "start", "fleet-api"], check=True)

    fieldnames = ["phase", "trial", "idempotency_key", "status_code", "latency_ms", "retry_after"]
    with open(args.out, "w", newline="") as f:
        w = csv.DictWriter(f, fieldnames=fieldnames)
        w.writeheader()
        for r in rows:
            w.writerow({k: r.get(k, "") for k in fieldnames})
    print(f"datos guardados en {args.out}")

    if not args.no_plot:
        plot_latencies(rows, args.plot_out)

    for phase in ("baseline_up", "fleet_down"):
        s = summarize(rows, phase)
        if s:
            print(
                f"{phase}: n={s['n']} media={s['mean_ms']:.1f}ms "
                f"desv={s['stdev_ms']:.1f}ms mediana={s['median_ms']:.1f}ms "
                f"min={s['min_ms']:.1f}ms max={s['max_ms']:.1f}ms",
            )

    statuses_up = sorted({r["status_code"] for r in rows if r["phase"] == "baseline_up"})
    statuses_down = sorted({r["status_code"] for r in rows if r["phase"] == "fleet_down"})
    print(f"codigos http baseline: {statuses_up}")
    print(f"codigos http caída:    {statuses_down}")


if __name__ == "__main__":
    main()
