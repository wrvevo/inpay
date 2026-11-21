import { useEffect, useState, useRef } from "react";
import {
  RefreshCw,
  ShoppingBag,
  CreditCard,
  Wallet,
  ShieldCheck,
} from "lucide-react";
import { api, money, type Session } from "../api";
import { toCents } from "../domain.mjs";
import { Heading, Notice, Modal, Empty } from "../components/UI";

type Source = "PROMOTIONAL" | "CREDIT" | "BALANCE" | "VOUCHER" | "EXTERNAL";
type Account = {
  promotional?: number;
  promotionalReserved?: number;
  owner: string;
  creditor: string;
  contract: string;
  blocked: boolean;
  creditLimit: number;
  creditUsed: number;
  creditReserved: number;
  balance: number;
  balanceReserved: number;
  voucher: number;
  voucherReserved: number;
};
type Product = {
  sku: string;
  title: string;
  price: number;
  provider: string;
  active: boolean;
};
type Order = {
  id: string;
  owner: string;
  title: string;
  total: number;
  status: string;
  simulation: boolean;
  providerReference?: string;
  creditOutstanding: number;
  tenders: { source: Source; amount: number }[];
};
type Adjustment = {
  id: string;
  owner: string;
  kind: string;
  amount: number;
  evidence: string;
  requestedBy: string;
  approvedBy?: string;
  status: string;
};
type Entry = {
  id: string;
  owner: string;
  debitBook: string;
  creditBook: string;
  amount: number;
  reason: string;
  actor: string;
  reference: string;
  occurredAt: string;
};
type Data = {
  accounts: Account[];
  products: Product[];
  orders: Order[];
  adjustments: Adjustment[];
  journal: Entry[];
  account?: Account;
};
const sources: Source[] = [
  "PROMOTIONAL",
  "CREDIT",
  "BALANCE",
  "VOUCHER",
  "EXTERNAL",
];
const labels: Record<string, string> = {
  PROMOTIONAL: "Crédito promocional",
  PROMOTIONAL_GRANT: "Concessão de benefício",
  CREDIT: "Crédito contratual legado",
  BALANCE: "Saldo de repasses",
  VOUCHER: "Voucher conciliado",
  EXTERNAL: "Pagamento externo",
  RESERVED: "Reservado",
  PENDING_RECONCILIATION: "Aguardando conciliação",
  COMPENSATION_REQUIRED: "Compensação pendente",
  DELIVERED: "Entregue",
  CANCELLED: "Cancelado",
  REFUNDED: "Estornado",
  CREDIT_LIMIT: "Limite de crédito",
  SETTLED_REPASS: "Repasse conciliado",
  VERIFIED_VOUCHER: "Voucher verificado",
  CREDIT_REPAYMENT: "Pagamento de crédito",
  PENDING: "Pendente",
  APPROVED: "Aprovado",
};
const empty: Data = {
  accounts: [],
  products: [],
  orders: [],
  adjustments: [],
  journal: [],
};
const sampleAccount: Account = {
  owner: "customer",
  creditor: "PlacarPlay · demonstração",
  contract: "DEMO-CONTRATO",
  blocked: false,
  creditLimit: 100000,
  creditUsed: 15000,
  creditReserved: 7000,
  balance: 20000,
  balanceReserved: 3000,
  voucher: 5000,
  voucherReserved: 0,
};
const sample: Data = {
  ...empty,
  accounts: [sampleAccount],
  account: sampleAccount,
  products: [
    {
      sku: "DEMO-GAME-100",
      title: "Gift card gamer · demonstração",
      price: 10000,
      provider: "SIMULATED",
      active: true,
    },
  ],
  orders: [
    {
      id: "demo-order",
      owner: "customer",
      title: "Gift card gamer · demonstração",
      total: 10000,
      status: "RESERVED",
      simulation: true,
      creditOutstanding: 0,
      tenders: [
        { source: "CREDIT", amount: 7000 },
        { source: "BALANCE", amount: 3000 },
      ],
    },
  ],
};

/** APIs administrativas e do titular são separadas; o backend verifica escopo, tenant e proprietário. */
export default function Marketplace({
  session,
  customer = false,
}: {
  session: Session;
  customer?: boolean;
}) {
  const [data, setData] = useState<Data>(
    session.mode === "demo" ? sample : empty,
  );
  const [section, setSection] = useState(customer ? "catalog" : "accounts");
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");
  const [busy, setBusy] = useState(false);
  const [modal, setModal] = useState("");
  const [selected, setSelected] = useState<Product | null>(null);
  const [firstSource, setFirstSource] = useState<Source>("CREDIT");
  const [secondSource, setSecondSource] = useState<Source | "NONE">("BALANCE");
  const [firstAmount, setFirstAmount] = useState("70,00");
  const [filter, setFilter] = useState("");
  const commandKeys = useRef(new Map<string, string>());
  const inFlight = useRef(false);
  const connected = session.mode === "connected";
  async function reload() {
    if (!connected) return;
    setBusy(true);
    setError("");
    try {
      const result = await api(session, `credits/${customer ? "me" : "admin"}`);
      setData({
        ...empty,
        ...result,
        accounts: result.accounts || (result.account ? [result.account] : []),
      });
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }
  useEffect(() => {
    void reload();
  }, [session.mode, session.token, session.tenant, customer]);
  async function command(path: string, body: Record<string, unknown>) {
    if (!connected || inFlight.current) return;
    inFlight.current = true;
    const signature = JSON.stringify([path, body]);
    const operationId =
      commandKeys.current.get(signature) || crypto.randomUUID();
    commandKeys.current.set(signature, operationId);
    setBusy(true);
    setError("");
    setMessage("");
    try {
      await api(session, `credits/${path}`, { operationId, ...body });
      commandKeys.current.delete(signature);
      setModal("");
      setSelected(null);
      await reload();
      setMessage(
        "Operação registrada. Consulte o estado atualizado antes de continuar.",
      );
    } catch (e) {
      setError(
        (e as Error).message +
          " Consulte a operação antes de repetir uma compra com resultado desconhecido.",
      );
    } finally {
      inFlight.current = false;
      setBusy(false);
    }
  }
  function submit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const f = new FormData(event.currentTarget);
    try {
      if (modal === "account")
        void command("admin/accounts", {
          owner: String(f.get("owner")),
          creditor: String(f.get("creditor")),
          contract: String(f.get("contract")),
        });
      if (modal === "product")
        void command("admin/products", {
          product: {
            sku: String(f.get("sku")),
            title: String(f.get("title")),
            price: toCents(String(f.get("price"))),
            provider: String(f.get("provider")),
            active: true,
          },
        });
      if (modal === "adjustment")
        void command("admin/adjustments", {
          owner: String(f.get("owner")),
          kind: String(f.get("kind")),
          amount:
            f.get("kind") === "CREDIT_LIMIT" &&
            /^0([.,]0{1,2})?$/.test(String(f.get("amount")))
              ? 0
              : toCents(String(f.get("amount"))),
          evidence: String(f.get("evidence")),
          relatedOrder: f.get("relatedOrder") || null,
        });
    } catch (e) {
      setError((e as Error).message);
    }
  }
  let firstCents = 0;
  try {
    firstCents = toCents(firstAmount);
  } catch {
    /* Invalid input disables purchase. */
  }
  const remainder = selected ? selected.price - firstCents : 0;
  const validPurchase =
    !!selected &&
    firstCents > 0 &&
    (secondSource === "NONE"
      ? remainder === 0
      : remainder > 0 && secondSource !== firstSource);
  function purchase() {
    if (!selected || !validPurchase) return;
    const tenders = [{ source: firstSource, amount: firstCents }];
    if (secondSource !== "NONE")
      tenders.push({ source: secondSource, amount: remainder });
    void command("me/orders", { sku: selected.sku, tenders });
  }
  const accounts = data.accounts;
  const sum = (select: (a: Account) => number) =>
    accounts.reduce((total, a) => total + select(a), 0);
  const orders = data.orders.filter((order) =>
    `${order.owner} ${order.id} ${order.title}`
      .toLowerCase()
      .includes(filter.toLowerCase()),
  );
  const pending = orders.filter((o) =>
    ["PENDING_RECONCILIATION", "COMPENSATION_REQUIRED"].includes(o.status),
  );
  const stats = customer
    ? ([
        [
          "Benefícios disponíveis",
          sum((a) => (a.promotional ?? 0) - (a.promotionalReserved ?? 0)),
          ShoppingBag,
        ],
        [
          "Crédito disponível",
          sum((a) => a.creditLimit - a.creditUsed - a.creditReserved),
          CreditCard,
        ],
        [
          "Repasses disponíveis",
          sum((a) => a.balance - a.balanceReserved),
          Wallet,
        ],
        [
          "Vouchers disponíveis",
          sum((a) => a.voucher - a.voucherReserved),
          ShoppingBag,
        ],
        ["Crédito utilizado", sum((a) => a.creditUsed), ShieldCheck],
      ] as const)
    : ([
        [
          "Benefícios disponíveis",
          sum((a) => (a.promotional ?? 0) - (a.promotionalReserved ?? 0)),
          ShoppingBag,
        ],
        ["Limites aprovados", sum((a) => a.creditLimit), CreditCard],
        ["Crédito utilizado", sum((a) => a.creditUsed), Wallet],
        [
          "Valores internos reservados",
          sum((a) => a.creditReserved + a.balanceReserved + a.voucherReserved),
          ShieldCheck,
        ],
        [
          "Repasses disponíveis",
          sum((a) => a.balance - a.balanceReserved),
          ShoppingBag,
        ],
      ] as const);
  const sections = customer
    ? [
        ["catalog", "Comprar"],
        ["orders", "Minhas compras"],
        ["statement", "Meu extrato"],
        ["contract", "Meu crédito"],
      ]
    : [
        ["accounts", "Contas e saldos"],
        ["approvals", "Aprovações"],
        ["orders", "Pedidos"],
        ["reconciliation", "Conciliação"],
        ["catalog", "Catálogo"],
        ["statement", "Auditoria"],
      ];
  return (
    <div className="marketplace-page">
      <Heading
        title={
          customer ? "Meu crédito e minhas compras" : "Créditos e benefícios"
        }
        subtitle={
          customer
            ? "Acompanhe seu limite e combine duas formas de pagamento."
            : "Módulo independente · crédito, repasses e vouchers separados."
        }
      >
        <button
          className="button secondary"
          disabled={busy || !connected}
          onClick={() => void reload()}
        >
          <RefreshCw size={16} /> Atualizar
        </button>
      </Heading>
      <Notice>
        {connected
          ? "Em ambiente local, pedidos são simulados e não geram gift cards válidos. Crédito e repasses dependem de evidência e aprovação."
          : "Prévia com dados fictícios. Ative o modo conectado em Configurações para operar Credits local. Estes dados não alteram o gateway."}
      </Notice>
      {error && (
        <p role="alert" className="form-error">
          {error}
        </p>
      )}
      {message && (
        <p role="status" className="notice">
          {message}
        </p>
      )}
      <div className="mp-stats">
        {stats.map(([label, value, Icon]) => (
          <article key={label}>
            <Icon size={20} />
            <span>{label}</span>
            <strong>{money(value)}</strong>
          </article>
        ))}
      </div>
      <div className="mp-tabs" role="tablist" aria-label="Seções de Credits">
        {sections.map(([id, label]) => (
          <button
            role="tab"
            aria-selected={section === id}
            className={section === id ? "active" : ""}
            key={id}
            onClick={() => setSection(id)}
          >
            {label}
            {id === "reconciliation" && pending.length > 0
              ? ` (${pending.length})`
              : ""}
          </button>
        ))}
      </div>
      {section === "accounts" && (
        <section className="mp-section">
          <div className="mp-toolbar">
            <h2>Contas por titular</h2>
            <div>
              <button
                className="button secondary"
                disabled={!connected || busy}
                onClick={() => setModal("adjustment")}
              >
                Solicitar alteração
              </button>{" "}
              <button
                className="button primary"
                disabled={!connected || busy}
                onClick={() => setModal("account")}
              >
                Nova conta
              </button>
            </div>
          </div>
          <div className="mp-table">
            <table>
              <thead>
                <tr>
                  <th>Titular / concedente</th>
                  <th>Crédito disponível</th>
                  <th>Reserva de crédito</th>
                  <th>Repasses disponíveis</th>
                  <th>Vouchers disponíveis</th>
                  <th>Situação</th>
                </tr>
              </thead>
              <tbody>
                {accounts.map((a) => (
                  <tr key={a.owner}>
                    <td>
                      <strong>{a.owner}</strong>
                      <small>{a.creditor}</small>
                    </td>
                    <td>
                      {money(a.creditLimit - a.creditUsed - a.creditReserved)}
                    </td>
                    <td>{money(a.creditReserved)}</td>
                    <td>{money(a.balance - a.balanceReserved)}</td>
                    <td>{money(a.voucher - a.voucherReserved)}</td>
                    <td>
                      <button
                        className="button secondary"
                        disabled={!connected || busy}
                        onClick={() =>
                          void command("admin/accounts/block", {
                            owner: a.owner,
                            blocked: !a.blocked,
                          })
                        }
                      >
                        {a.blocked ? "Desbloquear" : "Bloquear"}
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          {!accounts.length && (
            <Empty title="Nenhuma conta cadastrada">
              Cadastre o subject Cognito do titular. No portal local, utilize
              customer.
            </Empty>
          )}
        </section>
      )}
      {section === "approvals" && (
        <section className="mp-section">
          <div className="mp-toolbar">
            <h2>Alterações de crédito e repasses</h2>
            <button
              className="button primary"
              disabled={!connected || busy}
              onClick={() => setModal("adjustment")}
            >
              Solicitar alteração
            </button>
          </div>
          <p>
            Solicitante e aprovador devem ser pessoas diferentes. Evidências
            financeiras não podem ser reutilizadas.
          </p>
          <div className="mp-table">
            <table>
              <thead>
                <tr>
                  <th>Titular</th>
                  <th>Tipo</th>
                  <th>Valor</th>
                  <th>Evidência</th>
                  <th>Solicitante</th>
                  <th>Status</th>
                  <th>Ação</th>
                </tr>
              </thead>
              <tbody>
                {data.adjustments.map((a) => (
                  <tr key={a.id}>
                    <td>{a.owner}</td>
                    <td>{labels[a.kind]}</td>
                    <td>{money(a.amount)}</td>
                    <td>{a.evidence}</td>
                    <td>{a.requestedBy}</td>
                    <td>{labels[a.status]}</td>
                    <td>
                      {a.status === "PENDING" && (
                        <button
                          className="button secondary"
                          disabled={!connected || busy}
                          onClick={() =>
                            void command(
                              `admin/adjustments/${a.id}/approve`,
                              {},
                            )
                          }
                        >
                          Aprovar evidência
                        </button>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          {!data.adjustments.length && (
            <Empty title="Nenhuma solicitação registrada" />
          )}
        </section>
      )}
      {(section === "orders" || section === "reconciliation") && (
        <section className="mp-section">
          <div className="mp-toolbar">
            <h2>
              {section === "reconciliation"
                ? "Pendências de conciliação"
                : "Compras e suas fontes"}
            </h2>
            <label>
              Buscar
              <input
                value={filter}
                onChange={(e) => setFilter(e.target.value)}
                placeholder="ID, titular ou produto"
              />
            </label>
          </div>
          <div className="mp-table">
            <table>
              <thead>
                <tr>
                  <th>Pedido</th>
                  <th>Composição</th>
                  <th>Total</th>
                  <th>Estado</th>
                  <th>Ações</th>
                </tr>
              </thead>
              <tbody>
                {(section === "reconciliation" ? pending : orders).map((o) => (
                  <tr key={o.id}>
                    <td>
                      <strong>{o.title}</strong>
                      <small>{o.id}</small>
                      <small>
                        {o.owner}
                        {o.simulation ? " · SIMULAÇÃO" : ""}
                      </small>
                      {o.providerReference && (
                        <small>Referência: {o.providerReference}</small>
                      )}
                    </td>
                    <td>
                      {o.tenders.map((t) => (
                        <small key={t.source}>
                          {labels[t.source]}: {money(t.amount)}
                        </small>
                      ))}
                    </td>
                    <td>{money(o.total)}</td>
                    <td>
                      <span className="badge">
                        {labels[o.status] || o.status}
                      </span>
                    </td>
                    <td>
                      {customer ? (
                        o.status === "RESERVED" && (
                          <button
                            className="button secondary"
                            disabled={!connected || busy}
                            onClick={() =>
                              void command(`me/orders/${o.id}/cancel`, {})
                            }
                          >
                            Cancelar reserva
                          </button>
                        )
                      ) : (
                        <>
                          {o.status === "RESERVED" && (
                            <button
                              className="button primary"
                              disabled={!connected || busy}
                              onClick={() =>
                                void command(`admin/orders/${o.id}/process`, {})
                              }
                            >
                              Processar
                            </button>
                          )}
                          {[
                            "PENDING_RECONCILIATION",
                            "COMPENSATION_REQUIRED",
                          ].includes(o.status) && (
                            <button
                              className="button secondary"
                              disabled={!connected || busy}
                              onClick={() =>
                                void command(
                                  `admin/orders/${o.id}/reconcile`,
                                  {},
                                )
                              }
                            >
                              Consultar evidências
                            </button>
                          )}
                          {o.status === "DELIVERED" && (
                            <button
                              className="button secondary"
                              disabled={!connected || busy}
                              onClick={() => {
                                if (
                                  window.confirm(
                                    "Solicitar estorno integral às fontes originais?",
                                  )
                                )
                                  void command(
                                    `admin/orders/${o.id}/refund`,
                                    {},
                                  );
                              }}
                            >
                              Estornar
                            </button>
                          )}
                        </>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          {!(section === "reconciliation" ? pending : orders).length && (
            <Empty title="Nenhum pedido nesta visão" />
          )}
          {section === "reconciliation" && (
            <Notice>
              Resultados desconhecidos conservam reservas. A consulta ao
              fornecedor não autoriza emissão duplicada nem liberação sem
              confirmação.
            </Notice>
          )}
        </section>
      )}
      {section === "catalog" && (
        <section className="mp-section">
          <div className="mp-toolbar">
            <h2>
              {customer ? "Produtos elegíveis" : "Catálogo de benefícios"}
            </h2>
            {!customer && (
              <button
                className="button primary"
                disabled={!connected || busy}
                onClick={() => setModal("product")}
              >
                Novo produto
              </button>
            )}
          </div>
          <div className="mp-products">
            {data.products.map((p) => (
              <article key={p.sku}>
                <ShoppingBag size={28} />
                <small>{p.sku}</small>
                <h3>{p.title}</h3>
                <strong>{money(p.price)}</strong>
                <p>
                  {p.provider.startsWith("SIMULATED")
                    ? "Demonstração · sem valor comercial"
                    : p.provider}
                </p>
                {customer && (
                  <button
                    className="button primary"
                    disabled={!connected || busy || data.account?.blocked}
                    onClick={() => {
                      setSelected(p);
                      setFirstAmount((p.price / 200).toFixed(2));
                      setFirstSource("CREDIT");
                      setSecondSource("BALANCE");
                    }}
                  >
                    Combinar pagamentos
                  </button>
                )}
              </article>
            ))}
          </div>
          {!data.products.length && <Empty title="Nenhum produto disponível" />}
        </section>
      )}
      {section === "statement" && (
        <section className="mp-section">
          <h2>
            {customer
              ? "Histórico de movimentações"
              : "Journal operacional imutável"}
          </h2>
          <div className="mp-table">
            <table>
              <thead>
                <tr>
                  <th>Data</th>
                  <th>Evento</th>
                  <th>Origem / destino</th>
                  <th>Valor</th>
                  {!customer && <th>Responsável</th>}
                  <th>Referência</th>
                </tr>
              </thead>
              <tbody>
                {data.journal.map((e) => (
                  <tr key={e.id}>
                    <td>{new Date(e.occurredAt).toLocaleString("pt-BR")}</td>
                    <td>{e.reason}</td>
                    <td>
                      <small>{e.debitBook}</small>
                      <small>{e.creditBook}</small>
                    </td>
                    <td>{money(e.amount)}</td>
                    {!customer && <td>{e.actor}</td>}
                    <td>{e.reference}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          {!data.journal.length && (
            <Empty title="Nenhum lançamento registrado" />
          )}
        </section>
      )}
      {section === "contract" && (
        <section className="mp-section">
          <h2>Condições e utilização do crédito</h2>
          {data.account && (
            <dl className="mp-contract">
              <dt>Concedente</dt>
              <dd>{data.account.creditor}</dd>
              <dt>Referência do contrato</dt>
              <dd>{data.account.contract}</dd>
              <dt>Limite aprovado</dt>
              <dd>{money(data.account.creditLimit)}</dd>
              <dt>Crédito utilizado</dt>
              <dd>{money(data.account.creditUsed)}</dd>
              <dt>Limite reservado</dt>
              <dd>{money(data.account.creditReserved)}</dd>
              <dt>Situação</dt>
              <dd>
                {data.account.blocked
                  ? "Bloqueado"
                  : "Disponível para novas compras"}
              </dd>
            </dl>
          )}
          <Notice>
            Este módulo registra utilização e amortização por compra. Parcelas,
            juros e vencimentos dependem do produto contratado com a concedente.
          </Notice>
        </section>
      )}
      {modal && (
        <Modal
          title={
            modal === "account"
              ? "Cadastrar conta"
              : modal === "product"
                ? "Cadastrar produto"
                : "Solicitar alteração"
          }
          onClose={() => setModal("")}
        >
          <form className="mp-form" onSubmit={submit}>
            {modal !== "product" && (
              <label>
                Titular (subject Cognito; customer no local)
                <input
                  name="owner"
                  required
                  maxLength={200}
                  defaultValue="customer"
                />
              </label>
            )}
            {modal === "account" && (
              <>
                <label>
                  Concedente
                  <input name="creditor" required defaultValue="PlacarPlay" />
                </label>
                <label>
                  Referência do contrato
                  <input name="contract" required />
                </label>
              </>
            )}
            {modal === "product" && (
              <>
                <label>
                  SKU
                  <input name="sku" required />
                </label>
                <label>
                  Nome
                  <input name="title" required />
                </label>
                <label>
                  Preço em reais
                  <input
                    name="price"
                    required
                    inputMode="decimal"
                    placeholder="100,00"
                  />
                </label>
                <label>
                  Fornecedor local
                  <select name="provider">
                    <option>SIMULATED</option>
                    <option>SIMULATED_TIMEOUT</option>
                    <option>SIMULATED_REJECT</option>
                    <option>SIMULATED_CAPTURE_FAILURE</option>
                  </select>
                </label>
              </>
            )}
            {modal === "adjustment" && (
              <>
                <label>
                  Tipo
                  <select name="kind">
                    {[
                      "PROMOTIONAL_GRANT",
                      "CREDIT_LIMIT",
                      "SETTLED_REPASS",
                      "VERIFIED_VOUCHER",
                      "CREDIT_REPAYMENT",
                    ].map((k) => (
                      <option key={k} value={k}>
                        {labels[k]}
                      </option>
                    ))}
                  </select>
                </label>
                <label>
                  Valor em reais
                  <input name="amount" required inputMode="decimal" />
                </label>
                <label>
                  Referência da evidência conciliada
                  <input name="evidence" required maxLength={200} />
                </label>
                <label>
                  Pedido relacionado (obrigatório para amortização)
                  <input name="relatedOrder" />
                </label>
                <Notice>
                  Outra identidade autorizada precisa aprovar. A solicitação não
                  libera saldo.
                </Notice>
              </>
            )}
            <button className="button primary" disabled={busy}>
              Registrar solicitação
            </button>
          </form>
        </Modal>
      )}
      {selected && (
        <Modal
          title="Combinar formas de pagamento"
          onClose={() => setSelected(null)}
        >
          <div className="mp-form">
            <h3>{selected.title}</h3>
            <p>
              Total: <strong>{money(selected.price)}</strong>
            </p>
            <label>
              Primeira fonte
              <select
                value={firstSource}
                onChange={(e) => setFirstSource(e.target.value as Source)}
              >
                {sources.map((key) => (
                  <option value={key} key={key}>
                    {labels[key]}
                  </option>
                ))}
              </select>
            </label>
            <label>
              Valor da primeira fonte
              <input
                value={firstAmount}
                onChange={(e) => setFirstAmount(e.target.value)}
                inputMode="decimal"
              />
            </label>
            <label>
              Segunda fonte
              <select
                value={secondSource}
                onChange={(e) =>
                  setSecondSource(e.target.value as Source | "NONE")
                }
              >
                <option value="NONE">Uma única forma de pagamento</option>
                {sources.map((key) => (
                  <option key={key} value={key}>
                    {labels[key]}
                  </option>
                ))}
              </select>
            </label>
            <p>
              Complemento: <strong>{money(Math.max(0, remainder))}</strong>
            </p>
            <Notice>
              A compra reserva as fontes internas. A entrega depende da
              confirmação de toda a composição. Pagamento externo é simulado no
              ambiente local.
            </Notice>
            <button
              className="button primary"
              disabled={busy || !validPurchase || !connected}
              onClick={purchase}
            >
              Confirmar reserva da compra
            </button>
          </div>
        </Modal>
      )}
    </div>
  );
}
