import re
import urllib.parse
import urllib.request
from http.cookiejar import CookieJar

STORE = "http://127.0.0.1:8199"
jar = CookieJar()
opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(jar))


def shop(method, url, data=None):
    encoded = None if data is None else urllib.parse.urlencode(data).encode()
    request = urllib.request.Request(url, data=encoded, method=method)
    if data is not None:
        request.add_header("Content-Type", "application/x-www-form-urlencoded")
    with opener.open(request, timeout=25) as response:
        return response.read().decode("utf-8", "replace")


home = shop("GET", STORE + "/")
csrf = re.search(r'name="csrf" value="([^"]+)"', home).group(1)
for method in ("pix", "credit_card", "boleto"):
    shop("POST", STORE + "/", {"csrf": csrf, "action": "add", "product": "1"})
    page = shop("GET", STORE + "/?page=checkout")
    token = re.search(r'name="csrf" value="([^"]+)"', page).group(1)
    key = re.search(r'name="checkout_key" value="([^"]+)"', page).group(1)
    shop(
        "POST",
        STORE + "/",
        {
            "csrf": token,
            "action": "checkout",
            "checkout_key": key,
            "method": method,
        },
    )
    orders = shop("GET", STORE + "/?page=orders")
    match = re.search(
        r"name=\"order\" value=\"" + re.escape(key) + r"\"",
        orders,
    )
    status = re.search(
        re.escape(method) + r"<br><span class=\"status\">([^<]+)</span>",
        orders,
    )
    print(method, "created" if match else "missing", status.group(1) if status else "no-status")
    if match:
        shop(
            "POST",
            STORE + "/",
            {"csrf": token, "action": "refresh", "order": key},
        )
        orders = shop("GET", STORE + "/?page=orders")
        status = re.search(
            re.escape(method) + r"<br><span class=\"status\">([^<]+)</span>",
            orders,
        )
        print(method, "after-refresh", status.group(1) if status else "no-status")
