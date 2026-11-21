import test from "node:test";
import assert from "node:assert/strict";
import {resolveRoute} from "../server/routes.mjs";
const env={PANEL_AUTH_MODE:"local"};
test("billing proxy allows management but never trusted settlement ingestion",()=>{
 assert.equal(resolveRoute("GET","/api/billing/resources/costs/page/2",env),"http://127.0.0.1:8113/billing/v1/resources/costs?page=2&size=20");
 assert.equal(resolveRoute("POST","/api/billing/internal/settlements",env),null);
 assert.equal(resolveRoute("GET","/api/billing/resources/billing_commands/page/1",env),null);
});
