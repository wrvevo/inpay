import { test } from "node:test";
import assert from "node:assert/strict";
import { once } from "node:events";
import {
  resolveRoute,
  upstreamAuthorization,
  permittedOrigin,
} from "../server/routes.mjs";
import { createPanelServer } from "../server/index.mjs";
const id = "11111111-1111-1111-1111-111111111111";
const env = {
  PANEL_AUTH_MODE: "local",
  LOCAL_PASSWORD: "test-only",
  PANEL_ORIGINS: "http://localhost:3080",
};
test("proxy route allowlist excludes financial writes and path injection", () => {
  assert.equal(
    resolveRoute("GET", `/api/transactions/${id}`, env),
    `http://127.0.0.1:8091/v1/transactions/${id}`,
  );
  for (const path of [
    "/api/http://evil.test",
    "/api/transactions/../../x",
    "/api/providers/cielo",
    "/api/links/123",
  ])
    assert.equal(resolveRoute("POST", path, env), null);
  assert.throws(() => resolveRoute("POST", "/api/links", {}));
});
test("credentials are explicit, bearer takes precedence, cross-site requests denied", () => {
  assert.equal(upstreamAuthorization({}, {}), null);
  assert.match(upstreamAuthorization({}, env), /^Basic /);
  assert.equal(
    upstreamAuthorization({ authorization: "Bearer supplied" }, env),
    "Bearer supplied",
  );
  assert.equal(
    upstreamAuthorization({ authorization: "Basic invalid" }, env),
    null,
  );
  assert.equal(
    permittedOrigin({ origin: "https://attacker.test" }, env),
    false,
  );
  assert.equal(permittedOrigin({ "sec-fetch-site": "cross-site" }, env), false);
});
test("HTTP proxy forwards tenant and JSON, rejects origin and hides upstream bodies", async () => {
  const calls = [];
  const server = createPanelServer(env, async (url, options) => {
    calls.push({ url, options });
    return calls.length === 1
      ? new Response(JSON.stringify({ id, status: "ACTIVE" }))
      : new Response("sensitive provider detail", { status: 403 });
  });
  server.listen(0, "127.0.0.1");
  await once(server, "listening");
  const base = `http://127.0.0.1:${server.address().port}`;
  try {
    const response = await fetch(`${base}/api/links`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        "X-Merchant-Id": id,
        Origin: "http://localhost:3080",
      },
      body: JSON.stringify({ amount: 100 }),
    });
    assert.equal(response.status, 200);
    assert.equal((await response.json()).id, id);
    assert.equal(calls[0].options.headers["X-Merchant-Id"], id);
    const denied = await fetch(`${base}/api/links`, {
      method: "POST",
      headers: { Origin: "https://attacker.test" },
    });
    assert.equal(denied.status, 403);
    assert.equal(calls.length, 1);
    const invalid = await fetch(`${base}/api/links`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: "bad",
    });
    assert.equal(invalid.status, 400);
    const failed = await fetch(`${base}/api/transactions/${id}`);
    assert.equal(failed.status, 403);
    assert.ok(!(await failed.text()).includes("sensitive"));
    assert.match(
      response.headers.get("content-security-policy"),
      /frame-ancestors 'none'/,
    );
  } finally {
    server.close();
    server.closeAllConnections();
  }
});
