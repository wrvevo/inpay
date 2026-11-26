import test from "node:test";
import assert from "node:assert/strict";
import { createUser, login, PASSWORD } from "../src/access.mjs";

test("manager cannot grant a menu the admin did not give them", () => {
  const manager = login("PANEL", "manager@inpay.local", PASSWORD);
  assert.equal(manager.role, "MANAGER");
  assert.throws(() =>
    createUser(manager, {
      role: "VIEWER",
      name: "Extra",
      email: "extra-menu@inpay.local",
      document: "10000000099",
      menus: ["clearsale"],
      stores: [manager.stores[0].id],
    }),
  );
  const created = createUser(manager, {
    role: "MANAGER",
    name: "Outro gerente",
    email: "outro-gerente@inpay.local",
    document: "10000000098",
    menus: ["fraud"],
    stores: [manager.stores[0].id],
  });
  assert.deepEqual(created.menus, ["fraud"]);
});
