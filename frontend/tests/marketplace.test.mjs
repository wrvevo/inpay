import { test } from "node:test";
import assert from "node:assert/strict";
import { once } from "node:events";
import { resolveRoute } from "../server/routes.mjs";
import { createPanelServer } from "../server/index.mjs";
const env = { PANEL_AUTH_MODE: "local", LOCAL_PASSWORD: "test-only" };
const id = "11111111-1111-1111-1111-111111111111";
test("credits allowlist is separate from financial provider routes", () => {
  assert.equal(
    resolveRoute("POST", `/api/credits/admin/orders/${id}/reconcile`, env),
    `http://127.0.0.1:8110/credits/v1/admin/orders/${id}/reconcile`,
  );
  assert.equal(
    resolveRoute("GET", "/api/credits/me", env),
    "http://127.0.0.1:8110/credits/v1/me",
  );
  for (const path of [
    "/api/credits/admin/execute",
    "/api/credits/me/../admin",
    "/api/credits/providers/cielo",
  ])
    assert.equal(resolveRoute("POST", path, env), null);
});
test("local customer bridge never uses admin identity or overrides supplied JWT", async () => {
  const calls = [];
  const server = createPanelServer(env, async (url, options) => {
    calls.push(options.headers.Authorization);
    return new Response("{}");
  });
  server.listen(0, "127.0.0.1");
  await once(server, "listening");
  const base = `http://127.0.0.1:${server.address().port}`;
  try {
    await fetch(base + "/api/credits/me");
    await fetch(base + "/api/credits/admin");
    await fetch(base + "/api/credits/me", {
      headers: { Authorization: "Bearer supplied" },
    });
    assert.equal(
      Buffer.from(calls[0].slice(6), "base64").toString(),
      "customer:test-only",
    );
    assert.equal(
      Buffer.from(calls[1].slice(6), "base64").toString(),
      "developer:test-only",
    );
    assert.equal(calls[2], "Bearer supplied");
  } finally {
    server.close();
    await once(server, "close");
  }
});
