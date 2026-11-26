"""Sessão funcional local. Não chama bancos reais: X-Mock true e o simulador."""
import json
import os
import re
import uuid
import urllib.error
import urllib.parse
import urllib.request
from http.cookiejar import CookieJar
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "docs" / "testing" / "functional-session-results.json"
AUTH = "Basic aW5wYXk6aW5wYXlAMTIz"
TENANT = "11111111-1111-1111-1111-111111111111"
STORE = os.environ.get("STORE_URL", "http://127.0.0.1:8199")
RESULTS = []


def record(name, ok, detail):
    RESULTS.append({"name": name, "ok": bool(ok), "detail": str(detail)[:500]})
    print(("PASS " if ok else "FAIL ") + name + " :: " + str(detail)[:180])


def call(method, url, body=None, headers=None, timeout=20):
    data = None if body is None else json.dumps(body).encode()
    request = urllib.request.Request(url, data=data, method=method)
    for key, value in (headers or {}).items():
        request.add_header(key, value)
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            raw = response.read().decode("utf-8", "replace")
            return response.status, raw
    except urllib.error.HTTPError as error:
        return error.code, error.read().decode("utf-8", "replace")
    except Exception as error:
        return 0, str(error)


def auth_headers(extra=None):
    headers = {
        "Authorization": AUTH,
        "X-Merchant-Id": TENANT,
        "Accept": "application/json",
    }
    if extra:
        headers.update(extra)
    return headers


def health():
    targets = {
        "merchant": 8081,
        "links": 8082,
        "checkout": 8083,
        "gateway": 8084,
        "routing": 8085,
        "token": 8086,
        "fraud": 8087,
        "webhook": 8088,
        "reconciliation": 8089,
        "notification": 8090,
        "query": 8091,
        "mock-server": 8098,
        "wiremock-stubs": 8099,
        "credits": 8110,
        "accounts": 8111,
        "marketplace": 8112,
        "billing": 8113,
        "cielo": 8101,
        "rede": 8102,
        "getnet": 8103,
        "stone": 8104,
        "bin": 8105,
        "global-payments": 8106,
        "pagbank": 8107,
        "safrapay": 8108,
        "c6": 8121,
        "itau": 8122,
        "santander": 8123,
        "bb": 8124,
        "bradesco": 8125,
        "caixa": 8126,
        "banrisul": 8127,
        "grafana": 3000,
        "prometheus": 9090,
        "panel": 3080,
        "game-store": 8199,
        "simulator": 8120,
    }
    for name, port in targets.items():
        path = "/actuator/health" if port not in (3000, 9090, 3080, 8199, 8099, 8120) else "/"
        if port == 3080:
            path = "/healthz"
        if port == 9090:
            path = "/-/ready"
        if port == 8120:
            path = "/health"
        if port == 8099:
            path = "/__admin/mappings"
        status, body = call("GET", f"http://127.0.0.1:{port}{path}", timeout=5)
        record(f"disponivel {name}:{port}", status and status < 500, f"HTTP {status} {body[:120]}")


def pages():
    pages = {
        "swagger-gateway": "http://127.0.0.1:8084/swagger-ui/index.html",
        "swagger-accounts": "http://127.0.0.1:8111/swagger-ui/index.html",
        "swagger-marketplace": "http://127.0.0.1:8112/swagger-ui/index.html",
        "swagger-credits": "http://127.0.0.1:8110/swagger-ui/index.html",
        "swagger-billing": "http://127.0.0.1:8113/swagger-ui/index.html",
        "panel": "http://127.0.0.1:3080/",
        "game-store": f"{STORE}/",
        "game-store-admin": f"{STORE}/admin",
        "grafana": "http://127.0.0.1:3000/login",
    }
    for name, url in pages.items():
        status, body = call("GET", url, timeout=8)
        record(name, status == 200 and len(body) > 50, f"HTTP {status} bytes={len(body)}")


def modules():
    checks = [
        ("accounts banks", "GET", "http://127.0.0.1:8111/accounts/v1/banks", None),
        ("marketplace recipients", "GET", "http://127.0.0.1:8112/marketplace/v1/recipients", None),
        ("credits admin", "GET", "http://127.0.0.1:8110/credits/v1/admin", None),
        ("billing invoices", "GET", "http://127.0.0.1:8113/billing/v1/invoices", None),
        ("gateway providers", "GET", "http://127.0.0.1:8084/gateway/v1/providers", None),
    ]
    for name, method, url, body in checks:
        status, raw = call(method, url, body, auth_headers())
        record(name, 200 <= status < 300, f"HTTP {status} {raw[:160]}")


def connectors():
    banks = {
        "c6": 8121,
        "itau": 8122,
        "santander": 8123,
        "bb": 8124,
        "bradesco": 8125,
        "caixa": 8126,
        "banrisul": 8127,
    }
    for bank, port in banks.items():
        status, raw = call(
            "GET",
            f"http://127.0.0.1:{port}/internal/v1/bank/operations",
            headers=auth_headers(),
        )
        record(f"catalogo {bank}", status == 200 and "PIX" in raw, f"HTTP {status} {raw[:140]}")
        status, raw = call(
            "POST",
            f"http://127.0.0.1:{port}/internal/v1/bank/operations/PIX_COB_POST",
            {"amount": 1000},
            auth_headers(
                {
                    "Content-Type": "application/json",
                    "Idempotency-Key": str(uuid.uuid4()),
                    "X-Mock": "true",
                }
            ),
        )
        record(
            f"conector mock {bank} PIX_COB_POST",
            status == 200 and ("mock" in raw.lower() or "pix" in raw.lower()),
            f"HTTP {status} {raw[:180]}",
        )
        status, raw = call(
            "POST",
            f"http://127.0.0.1:8084/gateway/v1/bank-operations/{bank}/PIX_COB_POST",
            {"amount": 1000},
            auth_headers(
                {
                    "Content-Type": "application/json",
                    "Idempotency-Key": str(uuid.uuid4()),
                    "X-Mock": "true",
                }
            ),
        )
        record(
            f"gateway mock {bank} PIX_COB_POST",
            status == 200 and "mock" in raw.lower(),
            f"HTTP {status} {raw[:180]}",
        )
    status, raw = call("GET", "http://127.0.0.1:8099/__admin/mappings", timeout=8)
    count = raw.count('"id"') if status == 200 else 0
    record("wiremock mappings", status == 200 and count > 0, f"HTTP {status} ids={count}")
    for acquirer, port in {
        "cielo": 8101,
        "rede": 8102,
        "getnet": 8103,
        "stone": 8104,
        "bin": 8105,
        "global-payments": 8106,
        "pagbank": 8107,
        "safrapay": 8108,
    }.items():
        status, raw = call(
            "GET",
            f"http://127.0.0.1:{port}/internal/v1/module",
            headers=auth_headers(),
        )
        record(f"modulo {acquirer}", status == 200, f"HTTP {status} {raw[:140]}")


def store_orders():
    jar = CookieJar()
    opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(jar))

    def shop(method, url, data=None):
        encoded = None if data is None else urllib.parse.urlencode(data).encode()
        request = urllib.request.Request(url, data=encoded, method=method)
        if data is not None:
            request.add_header("Content-Type", "application/x-www-form-urlencoded")
        try:
            with opener.open(request, timeout=25) as response:
                return response.status, response.geturl(), response.read().decode("utf-8", "replace")
        except urllib.error.HTTPError as error:
            return error.code, error.geturl(), error.read().decode("utf-8", "replace")
        except Exception as error:
            return 0, url, str(error)

    status, _, home = shop("GET", STORE + "/")
    csrf = re.search(r'name="csrf" value="([^"]+)"', home)
    record("loja vitrine", status == 200 and "product" in home, f"HTTP {status}")
    if not csrf:
        record("loja checkout", False, "csrf ausente")
        return
    methods = re.findall(r'name="method" value="([^"]+)"', shop("GET", STORE + "/?page=checkout")[2])
    # add product first
    shop("POST", STORE + "/", {"csrf": csrf.group(1), "action": "add", "product": "1"})
    record("loja meios", len(methods) >= 5, f"meios={len(methods)}")
    paid = 0
    for method in methods:
        page = shop("GET", STORE + "/?page=checkout")[2]
        token = re.search(r'name="csrf" value="([^"]+)"', page)
        key = re.search(r'name="checkout_key" value="([^"]+)"', page)
        if not token or not key or "Neon Horizon" not in page:
            shop("POST", STORE + "/", {"csrf": csrf.group(1), "action": "add", "product": "1"})
            page = shop("GET", STORE + "/?page=checkout")[2]
            token = re.search(r'name="csrf" value="([^"]+)"', page)
            key = re.search(r'name="checkout_key" value="([^"]+)"', page)
        if not token or not key:
            record(f"pedido {method}", False, "checkout sem chave")
            continue
        status, _, _ = shop(
            "POST",
            STORE + "/",
            {
                "csrf": token.group(1),
                "action": "checkout",
                "checkout_key": key.group(1),
                "method": method,
            },
        )
        orders = shop("GET", STORE + "/?page=orders")[2]
        ok = status in (200, 303) and method.split("_")[0] in orders or "PAID" in orders or "PENDING" in orders or "UNKNOWN" in orders
        if "PAID" in orders or "PENDING" in orders:
            paid += 1
        record(f"pedido {method}", status in (200, 303), f"HTTP {status} trecho={orders[orders.find(method[:12]):orders.find(method[:12])+80] if method[:12] in orders else orders[:80]}")
    record("pedidos com estado de transacao", paid > 0, f"com PAID ou PENDING={paid}")


def grafana(failures):
    text = "InPay functional session. failures=" + str(len(failures))
    if failures:
        text += " | " + " | ".join(item["name"] + ": " + item["detail"][:80] for item in failures[:12])
    status, raw = call(
        "POST",
        "http://127.0.0.1:3000/api/annotations",
        {"text": text[:2000], "tags": ["inpay", "functional-test"]},
        {
            "Authorization": "Basic YWRtaW46bG9jYWwtb25seS1jaGFuZ2UtbWU=",
            "Content-Type": "application/json",
        },
    )
    record("grafana annotation", status in (200, 201), f"HTTP {status} {raw[:160]}")


def main():
    health()
    pages()
    modules()
    connectors()
    store_orders()
    failures = [item for item in RESULTS if not item["ok"]]
    grafana(failures)
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(json.dumps(RESULTS, indent=2, ensure_ascii=False), encoding="utf-8")
    print(f"TOTAL {len(RESULTS)} FAIL {len(failures)}")


if __name__ == "__main__":
    main()
