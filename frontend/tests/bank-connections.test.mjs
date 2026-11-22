import test from "node:test";
import assert from "node:assert/strict";
import { resolveRoute } from "../server/routes.mjs";

test("bank configuration proxy cannot activate or execute banking operations", () => {
  const env = { PANEL_AUTH_MODE: "local" };
  assert.equal(
    resolveRoute("GET", "/api/bank-config/banks", env),
    "http://127.0.0.1:8111/accounts/v1/banks",
  );
  assert.equal(
    resolveRoute("POST", "/api/bank-config/connections", env),
    "http://127.0.0.1:8111/accounts/v1/connections",
  );
  for (const path of [
    "/api/bank-config/transfers",
    "/api/bank-config/connections/enable",
    "/api/bank-config/../transfers",
  ]) {
    assert.equal(resolveRoute("POST", path, env), null);
  }
});
