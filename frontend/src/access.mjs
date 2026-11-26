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

function company(tradeName) {
  return {
    legalName: "InPay Comércio de Laboratório Ltda",
    tradeName,
    situation: "ATIVA",
    situationLabel: "ATIVO",
    openedOn: "2020-03-12",
    legalNature: "Sociedade Empresária Limitada",
    size: "ME",
    branch: "MATRIZ",
    mainActivity: "Comércio varejista de artigos de tecnologia",
    email: "contato@inpay.local",
    phone: "11999990000",
    address: {
      zip: "01310100",
      street: "Avenida Paulista",
      number: "1000",
      complement: "Conjunto 101",
      district: "Bela Vista",
      city: "São Paulo",
      state: "SP",
    },
    partners: [
      { name: "Ana Ribeiro", role: "Sócia-administradora" },
      { name: "Carlos Mendes", role: "Sócio" },
    ],
    source: "Cadastro de laboratório",
    consultedAt: "2026-11-26T12:00:00.000Z",
  };
}

function cases() {
  return [
    {
      id: STORES[0].id,
      storeId: STORES[0].id,
      cnpj: CNPJ,
      storeName: "Loja Centro",
      ...company("InPay Lab Centro"),
      payments: ["pix", "credit_card"],
      connectors: { pix: "pagbank", credit_card: "cielo" },
      connectorSettings: {
        pagbank: { clientId: "pagbank-lab", baseUrl: "https://sandbox.api.pagseguro.com", secretRef: "secrets/pagbank" },
        cielo: { merchantId: "cielo-lab", baseUrl: "https://apisandbox.cieloecommerce.cielo.com.br", secretRef: "secrets/cielo" },
      },
      antifraud: "INPAY_RISK",
      gatewayMode: "HOMOLOGACAO",
      journeySubmitted: false,
      status: "EM_HOMOLOGACAO",
      accountsPrepared: false,
      demo: null,
    },
    {
      id: STORES[1].id,
      storeId: STORES[1].id,
      cnpj: CNPJ,
      storeName: "Loja Sul",
      ...company("InPay Lab Sul"),
      payments: ["pix", "boleto"],
      connectors: { pix: "stone", boleto: "getnet" },
      connectorSettings: {
        stone: { host: "sandbox.stone.com.br", secretRef: "secrets/stone" },
        getnet: { clientId: "getnet-lab", merchantId: "loja-lab", baseUrl: "https://api-homologacao.getnet.com.br", secretRef: "secrets/getnet" },
      },
      antifraud: "SERASA_EXPERIAN",
      gatewayMode: "HOMOLOGACAO",
      journeySubmitted: false,
      status: "EM_HOMOLOGACAO",
      accountsPrepared: false,
      demo: null,
    },
  ];
}

function field(key, label, value = "", required = true, secret = false) {
  return { key, label, value, required, secret };
}

function acquirerSeed() {
  const card = ["credit_card", "debit_card", "pix", "boleto"];
  const bank = ["pix", "boleto"];
  const secret = field("secretRef", "Referência do segredo no servidor", "", false, true);
  return [
    { id: "cielo", name: "Cielo", methods: card, integration: "INTEGRADO", active: true, fields: [field("merchantId", "Merchant ID"), field("baseUrl", "URL base", "https://apisandbox.cieloecommerce.cielo.com.br"), field("queryUrl", "URL de consulta", "https://apiquerysandbox.cieloecommerce.cielo.com.br", false), secret] },
    { id: "rede", name: "Rede", methods: card, integration: "INTEGRADO", active: true, fields: [field("clientId", "Client ID"), field("baseUrl", "URL base", "https://api.userede.com.br/desenvolvedores"), field("tokenUrl", "URL do token", "https://api.userede.com.br/desenvolvedores/v1/token", false), secret] },
    { id: "getnet", name: "Getnet", methods: card, integration: "INTEGRADO", active: true, fields: [field("clientId", "Client ID"), field("merchantId", "Merchant ID"), field("baseUrl", "URL base", "https://api-homologacao.getnet.com.br"), field("channel", "Canal", "digital", false), secret] },
    { id: "stone", name: "Stone", methods: card, integration: "INTEGRADO", active: true, fields: [field("host", "Host"), field("baseUrl", "URL base", "https://sandbox.openbank.stone.com.br", false), secret] },
    { id: "bin", name: "Bin / Fiserv", methods: card, integration: "INTEGRADO", active: true, fields: [field("merchantId", "Merchant ID"), field("baseUrl", "URL base", "https://sandbox.bin.com.br"), field("keyStore", "Keystore", "", false), secret] },
    { id: "global-payments", name: "Global Payments", methods: card, integration: "INTEGRADO", active: true, fields: [field("merchantId", "Merchant ID"), field("baseUrl", "URL base", "https://api.sandbox.globalpay.com"), secret] },
    { id: "pagbank", name: "PagBank", methods: card, integration: "INTEGRADO", active: true, fields: [field("clientId", "Client ID"), field("baseUrl", "URL base", "https://sandbox.api.pagseguro.com"), secret] },
    { id: "safrapay", name: "Safrapay", methods: card, integration: "INTEGRADO", active: true, fields: [field("merchantId", "Merchant ID"), field("baseUrl", "URL base", "https://api-sandbox.safrapay.com.br"), secret] },
    { id: "c6", name: "C6 Bank", methods: bank, integration: "INTEGRADO", active: true, fields: [field("clientId", "Client ID"), field("pixKey", "Chave PIX"), field("baseUrl", "URL base", "https://baas-api-sandbox.c6bank.info"), secret] },
    { id: "itau", name: "Itaú", methods: bank, integration: "INTEGRADO", active: true, fields: [field("clientId", "Client ID"), field("pixBaseUrl", "URL PIX", "https://devportal.itau.com.br/sandboxapi/pix_recebimentos_ext_v2/v2"), field("boletoBaseUrl", "URL boleto", "https://devportal.itau.com.br/sandboxapi/cash_management_ext_v2/v2", false), secret] },
    { id: "santander", name: "Santander", methods: bank, integration: "INTEGRADO", active: true, fields: [field("clientId", "Client ID"), field("baseUrl", "URL base", "https://trust-sandbox.api.santander.com.br"), secret] },
    { id: "bb", name: "Banco do Brasil", methods: bank, integration: "EM_INTEGRACAO", active: false, fields: [field("clientId", "Client ID"), field("baseUrl", "URL base", ""), secret] },
    { id: "bradesco", name: "Bradesco", methods: bank, integration: "EM_INTEGRACAO", active: false, fields: [field("clientId", "Client ID"), field("baseUrl", "URL base", ""), secret] },
    { id: "caixa", name: "Caixa", methods: bank, integration: "EM_INTEGRACAO", active: false, fields: [field("clientId", "Client ID"), field("baseUrl", "URL base", ""), secret] },
    { id: "banrisul", name: "Banrisul", methods: bank, integration: "EM_INTEGRACAO", active: false, fields: [field("clientId", "Client ID"), field("baseUrl", "URL base", ""), secret] },
  ];
}

const memory = { users: seed(), cases: cases(), acquirers: acquirerSeed(), resets: new Map() };

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

const DOCUMENT_CHECKS = ["company", "representative", "address", "bank"];

function documentChecklist(input) {
  return Object.fromEntries(DOCUMENT_CHECKS.map((key) => [key, input?.[key] === true]));
}

function copyCase(item) {
  return {
    ...item,
    documents: documentChecklist(item.documents),
    payments: [...(item.payments || [])],
    connectors: { ...(item.connectors || {}) },
    connectorSettings: Object.fromEntries(
      Object.entries(item.connectorSettings || {}).map(([id, values]) => [id, { ...values }]),
    ),
    partners: [...(item.partners || [])],
    address: { ...(item.address || {}) },
    demo: item.demo
      ? {
          ...item.demo,
          connectors: { ...(item.demo.connectors || {}) },
          payments: [...(item.demo.payments || [])],
          connectorSettings: Object.fromEntries(
            Object.entries(item.demo.connectorSettings || {}).map(([id, values]) => [id, { ...values }]),
          ),
        }
      : null,
  };
}

function copyAcquirer(item) {
  return { ...item, methods: [...item.methods], fields: item.fields.map((field) => ({ ...field })) };
}

function acquirerById(id) {
  return memory.acquirers.find((item) => item.id === id);
}

function journeyReady(item) {
  const address = item.address || {};
  if (!item.legalName || item.situation !== "ATIVA" || !address.street || !address.city) return false;
  if (!DOCUMENT_CHECKS.every((key) => item.documents?.[key] === true)) return false;
  if (!item.antifraud || !item.payments?.length) return false;
  return item.payments.every((method) => {
    const acquirer = acquirerById(item.connectors?.[method]);
    if (!acquirer || !acquirer.active || !acquirer.methods.includes(method)) return false;
    const saved = item.connectorSettings?.[acquirer.id] || {};
    return acquirer.fields
      .filter((field) => field.required && !field.secret)
      .every((field) => String(saved[field.key] || "").trim());
  });
}

export function acquirerCatalog() {
  return memory.acquirers.map(copyAcquirer);
}

export function activeAcquirers(method) {
  return memory.acquirers
    .filter((item) => item.active && (!method || item.methods.includes(method)))
    .map(copyAcquirer);
}

export function setAcquirerActive(role, id, active) {
  if (role !== "ADMIN") throw new Error("Só o administrador da homologação altera o catálogo.");
  const item = acquirerById(id);
  if (!item) throw new Error("Adquirente não encontrado.");
  item.active = Boolean(active);
  return copyAcquirer(item);
}

export function homologationCases() {
  return memory.cases.map(copyCase);
}

export function startHomologation(role, input) {
  if (!["PDV", "ADMIN"].includes(role)) throw new Error("Sem permissão para iniciar.");
  const cnpj = String(input.cnpj || "").replace(/\D/g, "");
  if (cnpj.length !== 14) throw new Error("CNPJ inválido.");
  if (!input.storeName || !input.legalName) throw new Error("A consulta do CNPJ está incompleta.");
  const id = crypto.randomUUID();
  const next = {
    ...input,
    documents: documentChecklist(),
    id,
    storeId: id,
    cnpj,
    storeName: String(input.storeName).trim(),
    payments: [],
    connectors: {},
    connectorSettings: {},
    antifraud: input.antifraud || "INPAY_RISK",
    gatewayMode: "HOMOLOGACAO",
    journeySubmitted: false,
    status: "EM_HOMOLOGACAO",
    accountsPrepared: false,
    demo: null,
    partners: [...(input.partners || [])],
    address: { ...(input.address || {}) },
  };
  memory.cases.push(next);
  return copyCase(next);
}

export function saveCase(input, role) {
  if (!["PDV", "ADMIN"].includes(role)) throw new Error("Sem permissão para editar.");
  const cnpj = String(input.cnpj || "").replace(/\D/g, "");
  if (cnpj.length !== 14) throw new Error("CNPJ inválido.");
  const id = input.id || crypto.randomUUID();
  const current = memory.cases.find((item) => item.id === id);
  if (!current || current.journeySubmitted || current.status === "APROVADA") {
    throw new Error("Loja inexistente ou jornada bloqueada para revisão.");
  }
  const next = {
    ...(current || {}),
    ...input,
    id,
    cnpj,
    payments: [...(input.payments || [])],
    connectors: { ...(input.connectors || {}) },
    connectorSettings: Object.fromEntries(
      Object.entries(input.connectorSettings || {}).map(([key, values]) => [key, { ...values }]),
    ),
    partners: [...(input.partners || [])],
    address: { ...(input.address || {}) },
    documents: documentChecklist(input.documents),
    storeId: current.storeId,
    status: current.status,
    journeySubmitted: false,
    accountsPrepared: false,
    demo: null,
    gatewayMode: "HOMOLOGACAO",
  };
  const index = memory.cases.findIndex((item) => item.id === id);
  if (index >= 0) memory.cases[index] = next;
  else memory.cases.push(next);
  return copyCase(next);
}

export function submitCase(id, role) {
  if (!["PDV", "ADMIN"].includes(role)) throw new Error("Sem permissão.");
  const item = memory.cases.find((entry) => entry.id === id);
  if (!item || item.journeySubmitted || item.status === "APROVADA") throw new Error("Loja não encontrada ou jornada bloqueada.");
  if (!journeyReady(item)) {
    throw new Error("Conclua cadastro ativo, checklist documental fictício, antifraude e configuração dos adquirentes.");
  }
  item.status = "EM_HOMOLOGACAO";
  item.journeySubmitted = true;
  item.accountsPrepared = false;
  item.demo = null;
  return copyCase(item);
}

export function decideCase(id, role, approve) {
  if (!["BACKOFFICE", "ADMIN"].includes(role)) throw new Error("Sem permissão para aprovar.");
  const item = memory.cases.find((entry) => entry.id === id);
  if (!item || item.status !== "EM_HOMOLOGACAO" || !item.journeySubmitted) {
    throw new Error("A jornada ainda não foi enviada para aprovação.");
  }
  if (approve && !journeyReady(item)) throw new Error("Revalide os requisitos da jornada.");
  item.status = approve ? "APROVADA" : "RECUSADA";
  item.accountsPrepared = false;
  item.journeySubmitted = approve;
  item.demo = approve
    ? {
        id: crypto.randomUUID(),
        name: `${item.storeName} · Demo`,
        mode: "SIMULACAO",
        cnpj: item.cnpj,
        company: item.legalName,
        payments: [...item.payments],
        connectors: { ...item.connectors },
        connectorSettings: Object.fromEntries(
          Object.entries(item.connectorSettings || {}).map(([key, values]) => [key, { ...values }]),
        ),
        antifraud: item.antifraud,
      }
    : null;
  return copyCase(item);
}

export function openAnotherStore(sourceId, storeName, role) {
  if (!["PDV", "ADMIN"].includes(role)) throw new Error("Sem permissão para abrir loja.");
  const source = memory.cases.find((item) => item.id === sourceId);
  if (!source || !String(storeName || "").trim()) throw new Error("Informe o nome da nova loja.");
  return startHomologation(role, {
    ...copyCase(source),
    storeName: String(storeName).trim(),
    cnpj: source.cnpj,
    legalName: source.legalName,
  });
}

export function cloneCase(sourceId, targetStoreId, role) {
  if (!["PDV", "ADMIN"].includes(role)) throw new Error("Sem permissão para clonar.");
  const source = memory.cases.find((item) => item.id === sourceId);
  const target = memory.cases.find((item) => item.storeId === targetStoreId);
  if (!source || !target || source.cnpj !== target.cnpj || source.storeId === target.storeId) {
    throw new Error("A cópia exige outra loja do mesmo CNPJ.");
  }
  if (target.journeySubmitted || target.status === "APROVADA") throw new Error("Loja bloqueada para revisão.");
  target.legalName = source.legalName;
  target.email = source.email;
  target.phone = source.phone;
  target.address = source.address;
  target.payments = [...source.payments];
  target.antifraud = source.antifraud;
  target.settlementAlias = source.settlementAlias;
  target.splitEnabled = source.splitEnabled;
  target.status = "RASCUNHO";
  target.documents = documentChecklist();
  target.journeySubmitted = false;
  target.demo = null;
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
