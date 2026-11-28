#!/usr/bin/env python3
"""Amostra concorrente de registro idempotente exclusivamente no Docker local."""
import base64
import concurrent.futures
import json
import math
import os
import time
import urllib.error
import urllib.request
import uuid
from pathlib import Path

BASE = "http://127.0.0.1:8084/v1/payments"
AUTH = "Basic " + base64.b64encode(("inpay:" + os.environ["LOCAL_PASSWORD"]).encode()).decode()

def submit(key, amount=1000, expected=202):
    """Registra um comando sintético; mede a latência HTTP e verifica o status."""
    body = {"merchantOrderId": "load-" + key, "amount": amount, "currency": "BRL",
            "instrumentToken": "ptok_synthetic_load_test_only_123456"}
    request = urllib.request.Request(BASE, method="POST", data=json.dumps(body).encode(),
        headers={"Authorization": AUTH, "Idempotency-Key": key, "Content-Type": "application/json"})
    start = time.perf_counter()
    try:
        with urllib.request.urlopen(request, timeout=15) as response:
            status, payload = response.status, json.load(response)
    except urllib.error.HTTPError as exc:
        status, payload = exc.code, {}
    elapsed = (time.perf_counter() - start) * 1000
    assert status == expected, (status, expected)
    if expected == 202:
        assert payload["status"] == "CREATED" and payload["amount"] == amount
    return key, payload.get("id"), elapsed

def main():
    """Confirma uma identidade de pagamento por chave sob concorrência; registra percentis."""
    keys = [str(uuid.uuid4()) for _ in range(12)]
    requests = keys * 4
    start = time.perf_counter()
    with concurrent.futures.ThreadPoolExecutor(max_workers=8) as executor:
        results = list(executor.map(submit, requests))
    seconds = time.perf_counter() - start
    ids = {}
    for key, payment_id, _ in results:
        assert payment_id
        ids.setdefault(key, set()).add(payment_id)
    assert all(len(group) == 1 for group in ids.values())
    assert len({next(iter(group)) for group in ids.values()}) == len(keys)
    submit(keys[0], amount=1001, expected=409)
    latencies = sorted(row[2] for row in results)
    report = {"scenario": "registration-only-synthetic", "requests": len(results),
              "concurrency": 8, "unique_payments": len(keys), "requests_per_second": len(results)/seconds,
              "p95_ms": latencies[math.ceil(0.95*len(latencies))-1],
              "p99_ms": latencies[math.ceil(0.99*len(latencies))-1]}
    Path("test-results").mkdir(exist_ok=True)
    Path("test-results/payment-load.json").write_text(json.dumps(report, indent=2))
    print(json.dumps(report))

if __name__ == "__main__":
    main()
