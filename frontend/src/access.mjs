/** Lab directory mirrored by painel-gerencial. Password of every example user is inpay@123. */
export const PASSWORD = "inpay@123";
export const MENUS = [
  "overview",
  "transactions",
  "fraud",
  "risk",
  "clearsale",
  "serasa",
  "methods",
  "billing",
  "accounts",
  "team",
  "settings",
];
const CNPJ = "12345678000190";
export const STORES = [
  { id: "11111111-1111-1111-1111-111111111111", name: "Loja Centro", cnpj: CNPJ },
  { id: "22222222-2222-2222-2222-222222222222", name: "Loja Sul", cnpj: CNPJ },
];

function seed() {
  return [
    person("PANEL", "ADMIN", "Administrador InPay", "admin@inpay.local", "10000000001", MENUS, STORES.map((s) => s.id)),
    person("PANEL", "MANAGER", "Gerente da loja", "manager@inpay.local", "10000000002", ["overview", "transactions", "fraud", "risk", "team"], [STORES[0].id]),
    person("PANEL", "VIEWER", "Consulta da loja", "viewer@inpay.local", "10000000003", ["overview", "transactions"], STORES.map((s) => s.id)),
    person("HOMOLOGATION", "PDV", "PDV InPay", "pdv@inpay.local", "10000000004", [], STORES.map((s) => s.id)),
    person("HOMOLOGATION", "BACKOFFICE", "Backoffice InPay", "backoffice@inpay.local", "10000000005", [], STORES.map((s) => s.id)),
    person("HOMOLOGATION", "ADMIN", "Admin de homologação", "homologacao@inpay.local", "10000000006", [], STORES.map((s) => s.id)),
  ];
}

function person(realm, role, name, email, document, menus, stores) {
  return { id: email, realm, role, name, email, document, password: PASSWORD, menus: [...menus], stores: [...stores] };
}

function cases() {
  return [
    {
      id: STORES[0].id,
      storeId: STORES[0].id,
      cnpj: CNPJ,
      legalName: "InPay Comércio de Laboratório Ltda",
      storeName: "Loja Centro",
      email: "contato@inpay.local",
      phone: "11999990000",
      address: "Rua do Laboratório, 100",
      payments: ["pix", "credit_card", "boleto"],
      antifraud: "INPAY_RISK",
      settlementAlias: "conta-liquidacao-local",
      splitEnabled: false,
      status: "EM_HOMOLOGACAO",
      accountsPrepared: false,
    },
    {
      id: STORES[1].id,
      storeId: STORES[1].id,
      cnpj: CNPJ,
      legalName: "InPay Comércio de Laboratório Ltda",
      storeName: "Loja Sul",
      email: "contato@inpay.local",
      phone: "11999990000",
      address: "Rua do Laboratório, 100",
      payments: ["pix", "credit_card", "boleto"],
      antifraud: "INPAY_RISK",
      settlementAlias: "conta-liquidacao-local",
      splitEnabled: false,
      status: "RASCUNHO",
      accountsPrepared: false,
    },
  ];
}

const memory = { users: seed(), cases: cases(), resets: new Map() };

export function grantPanel(actor, targetRole, menus) {
  if (!menus.every((item) => MENUS.includes(item))) throw new Error("Menu desconhecido.");
  if (actor.role === "ADMIN") {
    if (!["ADMIN", "MANAGER", "VIEWER"].includes(targetRole)) throw new Error("Perfil não atribuível.");
    return;
  }
  if (actor.role !== "MANAGER") throw new Error("Este perfil não cria usuários.");
  if (!["MANAGER", "VIEWER"].includes(targetRole)) throw new Error("Gerente só cria gerente ou consulta.");
  if (!menus.every((item) => actor.menus.includes(item))) {
    throw new Error("O gerente só repassa menus que o administrador já liberou para ele.");
  }
}

export function login(realm, login, password) {
  const key = String(login || "").trim().toLowerCase();
  const user = memory.users.find((item) => item.email === key || item.document === String(login || "").trim());
  if (!user || user.realm !== realm || user.password !== password) throw new Error("Credencial inválida.");
  return publicUser(user);
}

export function recover(login) {
  const key = String(login || "").trim().toLowerCase();
  const user = memory.users.find((item) => item.email === key || item.document === String(login || "").trim());
  if (!user) throw new Error("Credencial inválida.");
  const token = crypto.randomUUID();
  memory.resets.set(token, user.id);
  return token;
}

export function resetPassword(token, password) {
  const id = memory.resets.get(token);
  if (!id || String(password || "").length < 8) throw new Error("Redefinição inválida.");
  memory.resets.delete(token);
  memory.users.find((item) => item.id === id).password = password;
}

export function updateProfile(actorId, name, email, document) {
  const user = memory.users.find((item) => item.id === actorId);
  if (!user || !name || !email || !document) throw new Error("Dados inválidos.");
  user.name = name.trim();
  user.email = email.trim().toLowerCase();
  user.document = document.trim();
  return publicUser(user);
}

export function createUser(actor, input) {
  if (actor.realm === "PANEL") grantPanel(actor, input.role, input.menus);
  else if (actor.role !== "ADMIN" || !["ADMIN", "PDV", "BACKOFFICE"].includes(input.role)) {
    throw new Error("Só o admin de homologação cria usuários.");
  }
  if (memory.users.some((item) => item.email === input.email || item.document === input.document)) {
    throw new Error("Usuário já existe.");
  }
  const created = person(
    actor.realm,
    input.role,
    input.name,
    input.email,
    input.document,
    actor.realm === "PANEL" ? input.menus : [],
    input.stores?.length ? input.stores : actor.stores.map((item) => item.id),
  );
  created.id = crypto.randomUUID();
  memory.users.push(created);
  return publicUser(created);
}

export function setMenus(actor, userId, menus) {
  const target = memory.users.find((item) => item.id === userId && item.realm === actor.realm);
  if (!target) throw new Error("Usuário não encontrado.");
  grantPanel(actor, target.role, menus);
  target.menus = [...menus];
  return publicUser(target);
}

export function usersOf(actor) {
  return memory.users.filter((item) => item.realm === actor.realm).map(publicUser);
}

export function homologationCases() {
  return memory.cases.map((item) => ({ ...item, payments: [...item.payments] }));
}

export function saveCase(input, role) {
  if (!["PDV", "ADMIN"].includes(role)) throw new Error("Sem permissão para editar.");
  const cnpj = String(input.cnpj || "").replace(/\D/g, "");
  if (cnpj.length !== 14) throw new Error("CNPJ inválido.");
  const id = input.id || crypto.randomUUID();
  const next = { ...input, id, cnpj, payments: [...(input.payments || [])], accountsPrepared: false };
  const index = memory.cases.findIndex((item) => item.id === id);
  if (index >= 0) memory.cases[index] = next;
  else memory.cases.push(next);
  return next;
}

export function submitCase(id, role) {
  if (!["PDV", "ADMIN"].includes(role)) throw new Error("Sem permissão.");
  const item = memory.cases.find((entry) => entry.id === id);
  item.status = "EM_HOMOLOGACAO";
  return { ...item };
}

export function decideCase(id, role, approve) {
  if (!["BACKOFFICE", "ADMIN"].includes(role)) throw new Error("Sem permissão para aprovar.");
  const item = memory.cases.find((entry) => entry.id === id);
  if (item.status !== "EM_HOMOLOGACAO") throw new Error("A loja não está em homologação.");
  item.status = approve ? "APROVADA" : "RECUSADA";
  item.accountsPrepared = approve;
  return { ...item };
}

export function cloneCase(sourceId, targetStoreId, role) {
  if (!["PDV", "ADMIN"].includes(role)) throw new Error("Sem permissão para clonar.");
  const source = memory.cases.find((item) => item.id === sourceId);
  const target = memory.cases.find((item) => item.storeId === targetStoreId);
  if (!source || !target || source.cnpj !== target.cnpj || source.storeId === target.storeId) {
    throw new Error("A cópia exige outra loja do mesmo CNPJ.");
  }
  target.legalName = source.legalName;
  target.email = source.email;
  target.phone = source.phone;
  target.address = source.address;
  target.payments = [...source.payments];
  target.antifraud = source.antifraud;
  target.settlementAlias = source.settlementAlias;
  target.splitEnabled = source.splitEnabled;
  target.status = "RASCUNHO";
  target.accountsPrepared = false;
  return { ...target, payments: [...target.payments] };
}

export function storesOf(user) {
  return STORES.filter((item) => user.stores.some((id) => id === item.id || id.id === item.id));
}

function publicUser(user) {
  return {
    id: user.id,
    realm: user.realm,
    role: user.role,
    name: user.name,
    email: user.email,
    document: user.document,
    menus: [...user.menus],
    stores: STORES.filter((item) => user.stores.includes(item.id)),
  };
}
