import test from "node:test";
import assert from "node:assert/strict";
import {
  createUser,
  decideCase,
  login,
  PASSWORD,
  saveCase,
  startHomologation,
  submitCase,
} from "../src/access.mjs";

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

test("starting homologation opens the gateway and approval creates the demo store", () => {
  const admin = login("HOMOLOGATION", "homologacao@inpay.local", PASSWORD);
  const created = startHomologation(admin.role, {
    cnpj: "00000000000191",
    storeName: "Agência Demo",
    legalName: "Banco do Brasil SA",
    situation: "ATIVA",
    situationLabel: "ATIVO",
    address: { street: "SAUN Quadra 5", city: "Brasília", state: "DF" },
  });
  assert.equal(created.status, "EM_HOMOLOGACAO");
  assert.equal(created.gatewayMode, "HOMOLOGACAO");
  assert.equal(created.demo, null);
  assert.throws(() => submitCase(created.id, "PDV"));
  saveCase(
    {
      ...created,
      payments: ["pix", "credit_card"],
      connectors: { pix: "pagbank", credit_card: "cielo" },
      connectorSettings: {
        pagbank: { clientId: "pagbank-lab", baseUrl: "https://sandbox.api.pagseguro.com" },
        cielo: { merchantId: "cielo-lab", baseUrl: "https://apisandbox.cieloecommerce.cielo.com.br" },
      },
      antifraud: "INPAY_RISK",
    },
    "PDV",
  );
  submitCase(created.id, "PDV");
  const approved = decideCase(created.id, "ADMIN", true);
  assert.equal(approved.status, "APROVADA");
  assert.equal(approved.demo.name, "Agência Demo · Demo");
  assert.equal(approved.demo.connectors.credit_card, "cielo");
  assert.equal(approved.demo.connectorSettings.cielo.merchantId, "cielo-lab");
  assert.equal(approved.demo.antifraud, "INPAY_RISK");
});
