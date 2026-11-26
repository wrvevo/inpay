#!/usr/bin/env python3
"""Check every declared operation has an explicit mapping; optionally exercise local HTTP."""
import argparse
import base64
import json
import os
from pathlib import Path
import re
import urllib.error
import urllib.request
import uuid

ROOT = Path(__file__).resolve().parents[1]
BANK = ROOT / "libs/bank-connector/src/main"
PACKAGE = "br/com/includelabs/inpay"
BANKS = ("c6", "itau", "santander", "bb", "bradesco", "caixa", "banrisul")
PARTNERS = ("clearsale", "serasa-experian")
CARD_PROVIDERS = ("cielo", "rede", "getnet", "stone", "bin", "global-payments", "pagbank", "safrapay")
RISK = ("ASSESSMENT_ACCEPT", "ASSESSMENT_REVIEW", "ASSESSMENT_REJECT",
        "CUSTOMER_REVIEW", "DOCUMENT_PENDING", "PROVIDER_UNAVAILABLE")


def request(url, method, body=None, headers=None):
    req = urllib.request.Request(url, data=body, method=method, headers=headers or {})
    try:
        with urllib.request.urlopen(req, timeout=5) as response:
            return response.status, json.load(response)
    except urllib.error.HTTPError as error:
        try:
            return error.code, json.load(error)
        except ValueError:
            return error.code, {}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--running", action="store_true", help="Require running local Compose stack")
    parser.add_argument("--cards", action="store_true", help="Test running card connectors")
    parser.add_argument("--card-start", type=int, default=0)
    parser.add_argument("--card-end", type=int, default=8)
    args = parser.parse_args()
    rows = [json.loads(line) for line in
            (ROOT / "services/bank-mock-server/src/main/resources/intent-mappings.jsonl").read_text().splitlines()
            if line.strip()]
    intents = {row["request"]["urlPath"]: row for row in rows}
    assert len(intents) == len(rows), "duplicate intent mapping"
    native = {}
    for bank, cap in (("c6", "C6"), ("itau", "Itau"), ("santander", "Santander")):
        source = ROOT / f"services/connector-{bank}/src/main/java/{PACKAGE}/{bank}/domain/{cap}Operation.java"
        operations = re.findall(r"^\s+([A-Z][A-Z0-9_]+)\(\s*" + cap + r"Product\.", source.read_text(), re.M)
        assert operations, bank
        native[bank] = operations
        for operation in operations:
            route = f"/intents/{bank}/{operation}"
            assert route in intents, route
            assert intents[route]["response"]["jsonBody"]["operation"] == operation
    assert sum(map(len, native.values())) == 131, native
    for partner in PARTNERS:
        for intent in RISK:
            assert f"/intents/{partner}/{intent}" in intents
    card_source = ROOT / f"libs/connector-runtime/src/main/java/{PACKAGE}/connector/domain/ProviderOperation.java"
    card_ops = re.findall(r"^\s+([A-Z][A-Z0-9_]+)[,;]?\s*$", card_source.read_text(), re.M)
    assert card_ops
    for provider in CARD_PROVIDERS:
        for operation in card_ops:
            route = f"/intents/{provider}/{operation}"
            assert route in intents and intents[route]["response"]["jsonBody"]["productionApproved"] is False
    cielo_source = ROOT / f"services/connector-cielo/src/main/java/{PACKAGE}/cielo/domain/CieloOperation.java"
    cielo_ops = re.findall(r"^\s+([A-Z][A-Z0-9_]+)[,;]", cielo_source.read_text(), re.M)
    assert cielo_ops
    for operation in cielo_ops:
        assert f"/intents/cielo/{operation}" in intents
    bank_source = (BANK / f"java/{PACKAGE}/bank/BankOperation.java").read_text()
    operations = re.findall(r'^\s+([A-Z][A-Z0-9_]+)\("([A-Z]+)", "([^"]+)",', bank_source, re.M)
    assert operations
    for bank in BANKS:
        for operation, method, path in operations:
            row = json.loads((BANK / f"resources/wiremock/mappings/{bank}-{operation}.json").read_text())
            assert row["request"]["method"] == method
            sample = re.sub(r"\{[^}]+\}", "synthetic", f"/{bank}{path}")
            assert re.fullmatch(row["request"]["urlPathPattern"], sample), sample
            assert row["response"]["jsonBody"]["operation"] == operation
            if args.running:
                status, body = request("http://127.0.0.1:8199" + sample, method)
                assert status == row["response"]["status"] and body["operation"] == operation, (bank, operation, status)
    if args.running:
        for route, row in intents.items():
            status, body = request("http://127.0.0.1:8199" + route, "POST", b"")
            assert status == row["response"]["status"] and body == row["response"]["jsonBody"], route
        password = os.environ.get("LOCAL_PASSWORD", "inpay@123")
        auth = base64.b64encode(("developer:" + password).encode()).decode()
        headers = {"Authorization": "Basic " + auth, "X-Mock": "true", "Content-Type": "application/json"}
        for bank, port in (("c6", 8131), ("itau", 8132), ("santander", 8133)):
            for operation in native[bank]:
                headers["Idempotency-Key"] = str(uuid.uuid4())
                status, body = request(f"http://127.0.0.1:{port}/internal/v1/bank/operations/{operation}", "POST", b"{}", headers)
                assert status == 200 and body["status"] == 200 and body["mocked"] is True, (bank, operation, status)
            headers["X-Mock"] = "false"
            status, _ = request(f"http://127.0.0.1:{port}/internal/v1/bank/operations/PIX_COB_GET", "POST", b'{"txid":"synthetic"}', headers)
            assert status == 503, (bank, status)
            headers["X-Mock"] = "true"
            headers["Idempotency-Key"] = str(uuid.uuid4())
            status, body = request(
                f"http://127.0.0.1:8084/gateway/v1/bank-operations/{bank}/PIX_COB_GET",
                "POST", b"{}", headers)
            assert status == 200 and body["mocked"] is True and body["status"] == 200, (bank, status)
        for bank, port in zip(BANKS[3:], range(8134, 8138)):
            for operation, method, path in operations:
                payload = {key: "synthetic" for key in re.findall(r"\{([^}]+)\}", path)}
                headers["Idempotency-Key"] = str(uuid.uuid4())
                status, body = request(f"http://127.0.0.1:{port}/internal/v1/bank/operations/{operation}", "POST", json.dumps(payload).encode(), headers)
                assert status == 200 and body["status"] == 200 and body["mocked"] is True, (bank, operation, status)
            headers["Idempotency-Key"] = str(uuid.uuid4())
            status, body = request(f"http://127.0.0.1:8084/gateway/v1/bank-operations/{bank}/PIX_COB_GET", "POST", b'{"txid":"synthetic"}', headers)
            assert status == 200 and body["mocked"] is True and body["status"] == 200, (bank, status)
        for partner, port in (("clearsale", 8128), ("serasa-experian", 8129)):
            url = f"http://127.0.0.1:{port}/internal/v1/assessments"
            probes = ({}, {"vpnOrProxy": True}, {"knownCompromised": True}) if partner == "clearsale" else ({}, {"addressMismatch": True}, {"documentInconsistent": True})
            for probe, decision in zip(probes, ("ACCEPT", "REVIEW", "REJECT")):
                status, body = request(url, "POST", json.dumps(probe).encode(), headers)
                assert status == 200 and body["decision"] == decision and body["mock"] is True, (partner, decision, status)
                assert body["policyVersion"].endswith("-wiremock-v1"), partner
    if args.cards:
        password = os.environ.get("LOCAL_PASSWORD", "inpay@123")
        auth = base64.b64encode(("developer:" + password).encode()).decode()
        headers = {"Authorization": "Basic " + auth, "X-Mock": "true"}
        for provider, port in list(zip(CARD_PROVIDERS, range(8101, 8109)))[args.card_start:args.card_end]:
            for operation in card_ops + (cielo_ops if provider == "cielo" else []):
                status, body = request(f"http://127.0.0.1:{port}/internal/v1/provider-mocks/{operation}", "POST", b"", headers)
                assert status == 200 and body["mocked"] is True and body["productionApproved"] is False, (provider, operation, status)
            status, _ = request(f"http://127.0.0.1:{port}/internal/v1/provider-mocks/AUTHORIZE", "POST", b"", {"Authorization": "Basic " + auth, "X-Mock": "false"})
            assert status == 503, (provider, status)
        print("Card mock connector HTTP checks passed.")
    print(f"Catalog: {len(operations) * len(BANKS)} legacy bank mappings; {sum(map(len, native.values()))} native bank intents; {len(PARTNERS) * len(RISK)} partner intents.")
    print(f"Card provider intents: {len(card_ops) * len(CARD_PROVIDERS)} common and {len(cielo_ops)} Cielo collection.")
    print("Bank and risk HTTP checks passed." if args.running else "Static catalog checks passed.")


if __name__ == "__main__":
    main()
