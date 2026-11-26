#!/usr/bin/env python3
"""Synthetic end-to-end onboarding; all HTTP calls hit the running Docker stack."""
import base64
import json
import os
import re
import sys
import urllib.error
import urllib.parse
import urllib.request
from http.cookiejar import CookieJar
from pathlib import Path

PANEL = "http://127.0.0.1:3080/api/painel"
API = "/homologation/v2"
TENANT = "11111111-1111-1111-1111-111111111111"
AUTH = "Basic " + base64.b64encode(("inpay:" + os.environ["LOCAL_PASSWORD"]).encode()).decode()
FIXTURE = base64.b64encode(b"%PDF-1.4\n% INPAY-SYNTHETIC-DOCUMENT\n%%EOF\n").decode()
STATE = Path("test-results/onboarding-case.json")

def call(path, token=None, method="GET", body=None, status=200, base=PANEL):
    headers = {"Content-Type": "application/json", "X-Merchant-Id": TENANT}
    if token:
        headers["Authorization"] = token
    req = urllib.request.Request(base + path, method=method, headers=headers,
        data=None if body is None else json.dumps(body).encode())
    try:
        with urllib.request.urlopen(req, timeout=100) as response:
            code, data = response.status, response.read()
    except urllib.error.HTTPError as error:
        code, data = error.code, error.read()
    allowed = status if isinstance(status, tuple) else (status,)
    assert code in allowed, (method, path, code, allowed)
    return json.loads(data) if data else {}

def login(identity):
    data = call("/auth/login", method="POST", body={"realm":"HOMOLOGATION",
        "login":identity + "@inpay.local","password":"inpay@123"})
    return "Bearer " + data["token"]

pdv, reviewer, admin = login("pdv"), login("backoffice"), login("homologacao")
if sys.argv[1] == "create":
    call(API+"/cases", status=401)
    item = call(API+"/cases", admin, "POST", {"cnpj":"12345678000199",
        "legalName":"Empresa de Teste","storeName":"Demo CI","address":"Endereço fictício"})
    path = API+"/cases/"+item["id"]
    def step(name, token, body=None, status=200):
        return call(path+"/"+name,token,"POST",body or {},status)
    bindings = {"pix":"c6","credit_card":"cielo"}
    call(path+"/bindings",admin,"PUT",{"bindings":bindings})
    step("submit",admin,status=(400,409,422))
    step("documents",admin,{"kind":"company","data":base64.b64encode(b"not a PDF").decode()},status=(400,409,422))
    for kind in ("company","representative","address","bank"):
        doc = step("documents",admin,{"kind":kind,"data":FIXTURE})
        assert doc["scanSource"] == "SYNTHETIC_FIXTURE"
    assert step("submit",admin)["state"] == "DOCUMENT_REVIEW"
    step("decide",admin,{"approve":True},status=(400,403,409,422))
    step("decide",pdv,{"approve":True},status=403)
    for doc in call(path+"/documents",reviewer)["data"]:
        assert call(path+"/documents/"+doc["id"]+"/download",reviewer)["data"] == FIXTURE
        step("documents/"+doc["id"]+"/review",reviewer,{"approve":True})
    assert len(step("partners",reviewer)["partners"]) == 2
    assert step("decide",reviewer,{"approve":True})["state"] == "CADASTRAL_SANDBOX_APPROVED"
    item=step("provision",pdv)
    assert item["state"]=="SANDBOX_READY" and item["demoId"] and item["accountsReceipt"]
    receipt=call("/accounts/v1/sandbox-configurations/"+item["accountsReceipt"],AUTH,base="http://127.0.0.1:8111")
    assert receipt["productionEnabled"] is False and receipt["storeId"]==item["storeId"]
    item=step("test",pdv)
    assert item["state"]=="TECHNICAL_REVIEW" and len(item["tests"])>=6
    item=step("technical",reviewer)
    assert item["state"]=="SANDBOX_APPROVED"
    STATE.write_text(json.dumps({"id":item["id"],"demoId":item["demoId"]}))
    print("PASS document storage, independent reviews, partners, Accounts and connector evidence")
else:
    stored=json.loads(STATE.read_text())
    path=API+"/cases/"+stored["id"]
    item=call(path,reviewer)
    assert item["state"]=="SANDBOX_APPROVED" and item["demoId"]==stored["demoId"]
    assert len(call(path+"/documents",reviewer)["data"])==4
    assert len(call(path+"/audit",reviewer)["data"])>=12
    config=call(API+"/demo/"+item["demoId"],AUTH,base="http://127.0.0.1:8130")
    assert config["productionEnabled"] is False
    opener=urllib.request.build_opener(urllib.request.HTTPCookieProcessor(CookieJar()))
    def shop(path="/",data=None):
        req=urllib.request.Request("http://127.0.0.1:8099"+path,data=None if data is None else urllib.parse.urlencode(data).encode())
        return opener.open(req,timeout=30).read().decode()
    def field(page,name):
        return re.search(r'name="'+name+r'" value="([^"]+)"',page).group(1)
    page=shop("/?demo="+item["demoId"])
    csrf=field(page,"csrf")
    shop(data={"csrf":csrf,"action":"add","product":"1"})
    checkout=shop("/?page=checkout")
    assert 'value="credit_card"' in checkout and 'value="pix"' in checkout
    assert 'value="boleto"' not in checkout
    key=field(checkout,"checkout_key")
    shop(data={"csrf":field(checkout,"csrf"),"action":"checkout","checkout_key":key,"method":"pix"})
    orders=shop("/?page=orders")
    assert key in orders and re.search(r'class="status">(PAID|AUTHORIZED|PENDING)</span>',orders)
    print("PASS persistent restart and configured demo checkout")
