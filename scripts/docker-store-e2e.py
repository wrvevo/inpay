#!/usr/bin/env python3
"""Synthetic checkout, refresh, cancellation, and real HTTP Accounts/Marketplace checks."""
import base64
import json
import os
import re
import urllib.parse
import urllib.request
from http.cookiejar import CookieJar

STORE = "http://127.0.0.1:8099"
AUTH = "Basic " + base64.b64encode(("inpay:" + os.environ["LOCAL_PASSWORD"]).encode()).decode()
TENANT = "11111111-1111-1111-1111-111111111111"
opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(CookieJar()))

def shop(path="/", data=None):
    request = urllib.request.Request(STORE + path, data=None if data is None else urllib.parse.urlencode(data).encode())
    with opener.open(request, timeout=30) as response:
        assert response.status == 200
        return response.read().decode()

def field(page, name):
    match = re.search(r'name="' + name + r'" value="([^"]+)"', page)
    assert match, name
    return match.group(1)

def order_status(page, key):
    for row in re.findall(r"<tr>.*?</tr>", page, flags=re.S):
        if 'name="order" value="' + key + '"' in row:
            return re.search(r'class="status">([^<]+)</span>', row).group(1)
    raise AssertionError("Order not found")

for method in ("credit_card", "debit_card", "pix", "boleto", "transfer"):
    csrf = field(shop(), "csrf")
    shop(data={"csrf": csrf, "action": "add", "product": "1"})
    checkout = shop("/?page=checkout")
    csrf, key = field(checkout, "csrf"), field(checkout, "checkout_key")
    shop(data={"csrf": csrf, "action": "checkout", "checkout_key": key, "method": method})
    assert order_status(shop("/?page=orders"), key) in {"PAID", "AUTHORIZED", "PENDING"}
    shop(data={"csrf": csrf, "action": "refresh", "order": key})
    assert order_status(shop("/?page=orders"), key) == "PAID", method
    shop(data={"csrf": csrf, "action": "cancel", "order": key})
    assert order_status(shop("/?page=orders"), key) == "CANCELLED", method
    print("PASS store -> Gateway -> simulator -> refresh -> cancel", method)

for port, path in ((8111, "/accounts/v1/banks"), (8112, "/marketplace/v1/recipients"),
                   (8110, "/credits/v1/admin"), (8130, "/homologation/v1/cases")):
    request = urllib.request.Request(f"http://127.0.0.1:{port}{path}",
                                     headers={"Authorization": AUTH, "X-Merchant-Id": TENANT})
    with urllib.request.urlopen(request, timeout=10) as response:
        assert response.status == 200
        assert isinstance(json.load(response), (dict, list))
        print("PASS", path)
