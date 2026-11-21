import { useEffect, useRef, useState } from "react";
import {
  ArrowDownToLine,
  Plus,
  RefreshCw,
  Wallet,
  Building2,
  Receipt,
  CalendarClock,
  Bell,
  Layers3,
  ShieldCheck,
} from "lucide-react";
import { api, money, type Session } from "../api";
import { Heading, Metric, Notice, Modal, Empty } from "../components/UI";
import { toCents, csvCell } from "../domain.mjs";

type Row = Record<string, any>;
const tabs = [
  "Visão financeira",
  "Clientes",
  "Faturas",
  "Recorrência",
  "Custos",
  "Contas a pagar",
  "Comunicação",
  "Planos",
];
const resources: Record<string, string> = {
  Clientes: "merchants",
  Faturas: "invoices",
  Recorrência: "subscriptions",
  Custos: "costs",
  "Contas a pagar": "payables",
  Comunicação: "notifications",
};
const plans = [
  {
    id: "START-2026-11",
    name: "Start",
    monthlyCents: 0,
    successfulOperationCents: 49,
  },
  {
    id: "GROW-2026-11",
    name: "Growth",
    monthlyCents: 14900,
    successfulOperationCents: 35,
  },
  {
    id: "SCALE-2026-11",
    name: "Scale",
    monthlyCents: 49900,
    successfulOperationCents: 25,
  },
];
const labels: Record<string, string> = {
  APPROVED: "Homologado",
  PENDING: "Aguardando revisão",
  OPEN: "Em aberto",
  PAID: "Pago",
  PLANNED: "Previsto",
  PENDING_PROVIDER: "Aguardando provedor",
  CANCELLED_PAID: "Cancelado: fatura paga",
};
const demoMerchants: Row[] = [
  {
    id: "demo-1",
    tenant: "demo",
    name: "Placar Play · demonstração",
    plan: "SCALE-2026-11",
    status: "APPROVED",
  },
  {
    id: "demo-2",
    tenant: "demo",
    name: "Loja Horizonte · demonstração",
    plan: "GROW-2026-11",
    status: "APPROVED",
  },
  {
    id: "demo-3",
    tenant: "demo",
    name: "Studio Norte · demonstração",
    plan: "START-2026-11",
    status: "PENDING",
  },
];
const demoInvoices = [
  {
    id: "demo-f1",
    merchant_id: "demo-1",
    customer_ref: "Cliente 001",
    amount: 120000,
    method: "PIX",
    due_date: "2026-11-25",
    status: "OPEN",
  },
  {
    id: "demo-f2",
    merchant_id: "demo-2",
    customer_ref: "Cliente 002",
    amount: 49000,
    method: "BOLETO",
    due_date: "2026-11-28",
    status: "OPEN",
  },
  {
    id: "demo-f3",
    merchant_id: "demo-1",
    customer_ref: "Cliente 003",
    amount: 250000,
    method: "CREDIT_CARD",
    due_date: "2026-11-20",
    status: "PAID",
  },
];
const demoCosts = [
  {
    id: "demo-c1",
    invoice_id: "demo-f3",
    provider: "Exemplo",
    provider_operation: "DEMO",
    gross: 250000,
    provider_cost: 6250,
    gateway_fee: 25,
    plan: "SCALE-2026-11",
    settled_at: "2026-11-20",
    deposit_reference: "DEMONSTRAÇÃO",
  },
];

/** Backoffice de cobrança. Dados demonstrativos nunca são enviados para as APIs. */
export default function Billing({ session }: { session: Session }) {
  const [tab, setTab] = useState(tabs[0]);
  const [dashboard, setDashboard] = useState<Row>({
    summary: {},
    merchants: [],
    invoices: [],
    costs: [],
  });
  const [rows, setRows] = useState<Row[]>([]);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [busy, setBusy] = useState(false);
  const [modal, setModal] = useState("");
  const [filter, setFilter] = useState("");
  const [page, setPage] = useState(1);
  const [revision, setRevision] = useState(0);
  const keys = useRef(new Map<string, string>());
  const sending = useRef(false);
  const demo = session.mode === "demo";
  useEffect(() => {
    let active = true;
    setError("");
    setRows([]);
    if (demo) {
      setDashboard({
        merchants: demoMerchants,
        invoices: demoInvoices,
        costs: demoCosts,
        summary: {
          approved_merchants: 2,
          receivable: 169000,
          payable: 85000,
          gateway_revenue: 25,
          provider_cost: 6250,
          settled_gross: 250000,
        },
      });
      setRows(
        tab === "Clientes"
          ? demoMerchants
          : tab === "Faturas"
            ? demoInvoices
            : tab === "Custos"
              ? demoCosts
              : [],
      );
      return () => {
        active = false;
      };
    }
    setBusy(true);
    Promise.all([
      api(session, `billing/dashboard/page/${page}`),
      resources[tab]
        ? api(session, `billing/resources/${resources[tab]}/page/${page}`)
        : Promise.resolve({ data: [] }),
    ])
      .then(([data, list]) => {
        if (active) {
          setDashboard(data);
          setRows(list.data);
        }
      })
      .catch((e: Error) => {
        if (active) setError(e.message);
      })
      .finally(() => {
        if (active) setBusy(false);
      });
    return () => {
      active = false;
    };
  }, [session, tab, page, revision]);
  async function command(path: string, body: Row = {}) {
    if (demo || sending.current) return;
    sending.current = true;
    setBusy(true);
    setError("");
    setNotice("");
    const signature = JSON.stringify([path, body]);
    const key = keys.current.get(signature) ?? crypto.randomUUID();
    keys.current.set(signature, key);
    try {
      await api(session, `billing/${path}`, { operationId: key, ...body });
      keys.current.delete(signature);
      setModal("");
      setRevision((v) => v + 1);
      setNotice(
        "Solicitação registrada. Confirme o estado na lista; registro não representa execução financeira.",
      );
    } catch (e) {
      setError((e as Error).message);
    } finally {
      sending.current = false;
      setBusy(false);
    }
  }
  function submit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const text = (key: string) => String(form.get(key) ?? "");
    try {
      if (modal === "merchant")
        void command("merchants", {
          tenant: text("tenant"),
          name: text("name"),
          plan: text("plan"),
          evidence: text("evidence"),
        });
      if (modal === "invoice" || modal === "subscription") {
        const amount = toCents(text("amount"));
        const conditions = {
          fineBps: Number(text("fineBps") || 0),
          dailyInterestBps: Number(text("dailyInterestBps") || 0),
          discountCents: text("discount") ? toCents(text("discount")) : 0,
          discountUntil: text("discountUntil") || null,
          maxSurchargeBps: Number(text("maxSurchargeBps") || 0),
        };
        const body = {
          merchantId: text("merchantId"),
          customerRef: text("customerRef"),
          amount,
          method: text("method"),
          conditions,
        };
        if (modal === "invoice")
          void command("invoices", {
            ...body,
            dueDate: text("dueDate"),
            contractReference: text("reference"),
          });
        else
          void command("subscriptions", {
            ...body,
            firstDue: text("dueDate"),
            anchorDay: Number(text("anchorDay")),
            mandateReference: text("reference"),
          });
      }
      if (modal === "payable")
        void command("payables", {
          merchantId: text("merchantId"),
          description: text("description"),
          amount: toCents(text("amount")),
          dueDate: text("dueDate"),
        });
      if (modal === "reminder")
        void command("reminders", {
          invoiceId: text("invoiceId"),
          channel: text("channel"),
          recipientReference: text("recipientReference"),
          template: text("template"),
          consentReference: text("consentReference"),
          scheduledOn: text("scheduledOn"),
        });
    } catch (e) {
      setError((e as Error).message);
    }
  }
  const summary = dashboard.summary ?? {};
  const merchants: Row[] = dashboard.merchants ?? [];
  const invoices: Row[] = dashboard.invoices ?? [];
  const filtered = rows.filter((row) =>
    JSON.stringify(row).toLowerCase().includes(filter.toLowerCase()),
  );
  function exportPage() {
    const data = tab === tabs[0] ? dashboard.costs : filtered;
    if (!data?.length) return;
    const fields = Object.keys(data[0]);
    const csv = [
      fields.map(csvCell).join(";"),
      ...data.map((row: Row) =>
        fields
          .map((field) =>
            csvCell(
              typeof row[field] === "object"
                ? JSON.stringify(row[field])
                : row[field],
            ),
          )
          .join(";"),
      ),
    ].join("\r\n");
    const url = URL.createObjectURL(
      new Blob(["\ufeff" + csv], { type: "text/csv;charset=utf-8" }),
    );
    const a = document.createElement("a");
    a.href = url;
    a.download = "inpay-financeiro-pagina.csv";
    a.click();
    URL.revokeObjectURL(url);
  }
  const modalForTab: Record<string, string> = {
    Clientes: "merchant",
    Faturas: "invoice",
    Recorrência: "subscription",
    "Contas a pagar": "payable",
    Comunicação: "reminder",
  };
  const columns: Record<string, [string, string][]> = {
    Clientes: [
      ["name", "Cliente"],
      ["plan", "Plano"],
      ["status", "Homologação"],
      ["tenant", "Organização"],
    ],
    Faturas: [
      ["customer_ref", "Cliente final"],
      ["method", "Meio"],
      ["amount", "Valor"],
      ["due_date", "Vencimento"],
      ["status", "Situação"],
    ],
    Recorrência: [
      ["customer_ref", "Cliente final"],
      ["method", "Meio"],
      ["amount", "Valor"],
      ["next_on", "Próximo ciclo"],
      ["active", "Ativa"],
    ],
    Custos: [
      ["provider", "Provedor"],
      ["gross", "Bruto"],
      ["provider_cost", "Custo do provedor"],
      ["gateway_fee", "Receita InPay"],
      ["settled_at", "Liquidação"],
      ["deposit_reference", "Depósito"],
    ],
    "Contas a pagar": [
      ["description", "Descrição"],
      ["amount", "Valor"],
      ["due_date", "Vencimento"],
      ["status", "Situação"],
    ],
    Comunicação: [
      ["channel", "Canal"],
      ["template", "Modelo"],
      ["scheduled_on", "Agendamento"],
      ["status", "Entrega"],
    ],
  };
  function display(key: string, value: any) {
    if (["amount", "gross", "provider_cost", "gateway_fee"].includes(key))
      return money(Number(value ?? 0));
    if (typeof value === "boolean") return value ? "Sim" : "Não";
    return labels[value] ?? String(value ?? "—");
  }
  return (
    <div className="billing-workspace">
      <Heading
        title="Gestão financeira"
        subtitle="Cobranças, clientes homologados e custos da operação em um só lugar."
      >
        <button className="button" onClick={exportPage}>
          <ArrowDownToLine size={16} /> Exportar página
        </button>
        <button
          className="button"
          disabled={busy}
          onClick={() => setRevision((v) => v + 1)}
          aria-label="Atualizar financeiro"
        >
          <RefreshCw size={16} />
        </button>
      </Heading>
      <div className="billing-banner">
        <div>
          <span className="eyebrow">INPAY · OPERAÇÕES FINANCEIRAS</span>
          <h2>Visibilidade para cobrar melhor.</h2>
          <p>
            Acompanhe o que vence, o que foi liquidado e quanto cada operação
            custou.
          </p>
        </div>
        <div className="billing-banner-icon">
          <Wallet size={42} />
        </div>
      </div>
      <Notice>
        {demo
          ? "Demonstração identificada: números e clientes fictícios, ações financeiras desabilitadas."
          : "Área restrita ao time InPay. Acesso global exige grupo financeiro; homologação requer revisor independente."}
      </Notice>
      {error && (
        <div role="alert" className="billing-error">
          {error}
        </div>
      )}
      {notice && <Notice>{notice}</Notice>}
      <div className="metrics billing-metrics">
        <Metric
          label="Clientes homologados"
          value={String(summary.approved_merchants ?? 0)}
          note="Aprovação independente registrada"
        />
        <Metric
          label="A receber"
          value={money(Number(summary.receivable ?? 0))}
          note="Principal em aberto · não é saldo"
        />
        <Metric
          label="Liquidação confirmada"
          value={money(Number(summary.settled_gross ?? 0))}
          note="Eventos verificados pela integração"
        />
        <Metric
          label="Receita bruta InPay"
          value={money(Number(summary.gateway_revenue ?? 0))}
          note="Tarifa de sucesso · não representa lucro"
        />
      </div>
      <div
        className="billing-tabs"
        role="tablist"
        aria-label="Gestão financeira"
      >
        {tabs.map((name) => (
          <button
            role="tab"
            aria-selected={name === tab}
            className={name === tab ? "active" : ""}
            key={name}
            onClick={() => {
              setTab(name);
              setPage(1);
              setFilter("");
            }}
          >
            {name}
          </button>
        ))}
      </div>
      {tab === tabs[0] ? (
        <div className="billing-overview">
          <section className="panel">
            <div className="panel-head">
              <h3>Próximas cobranças</h3>
              <CalendarClock size={19} />
            </div>
            <div className="billing-timeline">
              {invoices
                .filter((i) => i.status === "OPEN")
                .slice(0, 5)
                .map((invoice) => (
                  <div key={invoice.id}>
                    <span className="billing-date">
                      {String(invoice.due_date).slice(5, 10)}
                    </span>
                    <div>
                      <strong>{invoice.customer_ref}</strong>
                      <small>{invoice.method} · emissão externa pendente</small>
                    </div>
                    <b>{money(invoice.amount)}</b>
                  </div>
                ))}
              {!invoices.length && <Empty />}
            </div>
          </section>
          <section className="panel">
            <div className="panel-head">
              <h3>Composição da operação</h3>
              <Layers3 size={19} />
            </div>
            <div className="billing-breakdown">
              {[
                ["Recebimento bruto", summary.settled_gross, "#2078e5"],
                ["Custo das adquirentes", summary.provider_cost, "#f49a4b"],
                ["Tarifa InPay", summary.gateway_revenue, "#15a286"],
              ].map(([label, value, color]) => (
                <div key={String(label)}>
                  <span>{label}</span>
                  <strong>{money(Number(value ?? 0))}</strong>
                  <div>
                    <i
                      style={{
                        width: `${Number(summary.settled_gross) ? Math.max(1, (Number(value ?? 0) / Number(summary.settled_gross)) * 100) : 0}%`,
                        background: String(color),
                      }}
                    />
                  </div>
                </div>
              ))}
              <p>
                Custos reais por evento. Tarifas de emissão de boleto não são
                lançadas como sucesso.
              </p>
            </div>
          </section>
          <section className="panel billing-controls">
            <ShieldCheck />
            <div>
              <h3>Conferência antes da liberação</h3>
              <p>
                Clientes pendentes exigem outro aprovador. O painel não permite
                marcar faturas como pagas manualmente.
              </p>
            </div>
          </section>
        </div>
      ) : tab === "Planos" ? (
        <>
          <Notice>
            Proposta comercial InPay. Tarifas de adquirência, antifraude,
            mensagens e impostos são contratadas separadamente. A mensalidade do
            plano não é cobrada automaticamente por esta tela.
          </Notice>
          <div className="billing-plans">
            {plans.map((plan, i) => (
              <article className={i === 1 ? "featured" : ""} key={plan.id}>
                <span className="eyebrow">
                  {i === 0
                    ? "COMECE SEM MENSALIDADE"
                    : i === 1
                      ? "OPERAÇÃO EM CRESCIMENTO"
                      : "ESCALA E CONTROLE"}
                </span>
                <h2>{plan.name}</h2>
                <strong>
                  {money(plan.monthlyCents)}
                  <small>/mês</small>
                </strong>
                <p>
                  + {money(plan.successfulOperationCents)} por operação
                  liquidada
                </p>
                <ul>
                  <li>Gestão de cobranças e custos</li>
                  <li>APIs e conciliação operacional</li>
                  <li>Boleto: tarifa somente ao pagar</li>
                  <li>Condições sujeitas ao contrato</li>
                </ul>
                <span className="billing-plan-state">Proposta · {plan.id}</span>
              </article>
            ))}
          </div>
          <Notice>
            Enterprise: proposta sob medida. Sem promessa de participantes
            ilimitados, licença regulatória ou SLA ainda não contratado.
          </Notice>
        </>
      ) : (
        <section className="panel">
          <div className="panel-head">
            <h3>{tab}</h3>
            <div className="actions">
              <input
                aria-label="Buscar na página"
                placeholder="Buscar nesta página"
                value={filter}
                onChange={(e) => setFilter(e.target.value)}
              />
              {tab === "Recorrência" && (
                <button
                  className="button"
                  disabled={demo || busy}
                  onClick={() => void command("cycles/run")}
                >
                  Gerar ciclos devidos
                </button>
              )}
              {modalForTab[tab] && (
                <button
                  className="button primary"
                  disabled={demo || busy}
                  onClick={() => setModal(modalForTab[tab])}
                >
                  <Plus size={16} /> Cadastrar
                </button>
              )}
            </div>
          </div>
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  {columns[tab]?.map(([key, label]) => (
                    <th key={key}>{label}</th>
                  ))}
                  <th>Referência</th>
                  {["Clientes", "Recorrência"].includes(tab) && <th>Ação</th>}
                </tr>
              </thead>
              <tbody>
                {filtered.map((row) => (
                  <tr key={row.id}>
                    {columns[tab]?.map(([key]) => (
                      <td key={key}>{display(key, row[key])}</td>
                    ))}
                    <td>
                      <code title={row.id}>{String(row.id).slice(0, 12)}</code>
                    </td>
                    {tab === "Clientes" && (
                      <td>
                        {row.status === "PENDING" ? (
                          <button
                            className="button"
                            disabled={demo || busy}
                            onClick={() =>
                              void command(`merchants/${row.id}/approve`)
                            }
                          >
                            Revisar e aprovar
                          </button>
                        ) : (
                          "—"
                        )}
                      </td>
                    )}
                    {tab === "Recorrência" && (
                      <td>
                        <button
                          className="button"
                          disabled={demo || busy || !row.active}
                          onClick={() =>
                            void command(`subscriptions/${row.id}/cancel`)
                          }
                        >
                          Cancelar futuros
                        </button>
                      </td>
                    )}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          {!filtered.length && (
            <Empty title="Nenhum registro nesta página">
              Use as APIs ou os formulários para cadastrar. Modo demonstrativo
              não grava informações.
            </Empty>
          )}
          <div className="billing-pagination">
            <button
              className="button"
              disabled={page === 1 || busy}
              onClick={() => setPage((p) => p - 1)}
            >
              Anterior
            </button>
            <span>Página {page} · até 20 registros</span>
            <button
              className="button"
              disabled={demo || rows.length < 20 || busy}
              onClick={() => setPage((p) => p + 1)}
            >
              Próxima
            </button>
          </div>
        </section>
      )}
      {tab === "Comunicação" && (
        <Notice>
          Mensagens aguardam provedor contratado de SMS, e-mail ou WhatsApp.
          Nenhuma será apresentada como entregue sem confirmação.
        </Notice>
      )}
      {modal && (
        <Modal
          title={
            {
              merchant: "Homologar cliente",
              invoice: "Nova fatura",
              subscription: "Nova recorrência",
              payable: "Conta a pagar",
              reminder: "Agendar lembrete",
            }[modal] ?? "Cadastro"
          }
          onClose={() => setModal("")}
        >
          <form className="form-grid" onSubmit={submit}>
            {modal === "merchant" ? (
              <>
                <label>
                  Razão social
                  <input name="name" required maxLength={200} />
                </label>
                <label>
                  Tenant UUID
                  <input name="tenant" required />
                </label>
                <label>
                  Plano
                  <select name="plan">
                    {plans.map((p) => (
                      <option value={p.id} key={p.id}>
                        {p.name}
                      </option>
                    ))}
                  </select>
                </label>
                <label>
                  Evidência contratual
                  <input name="evidence" required />
                </label>
                <Notice>
                  O cadastro fica pendente até revisão de outra pessoa
                  autorizada.
                </Notice>
              </>
            ) : modal === "reminder" ? (
              <>
                <label>
                  Fatura UUID
                  <input name="invoiceId" required />
                </label>
                <label>
                  Canal
                  <select name="channel">
                    <option>EMAIL</option>
                    <option>SMS</option>
                    <option>WHATSAPP</option>
                  </select>
                </label>
                <label>
                  Referência do destinatário
                  <input name="recipientReference" required />
                </label>
                <label>
                  Template aprovado
                  <input name="template" required />
                </label>
                <label>
                  Referência do consentimento
                  <input name="consentReference" required />
                </label>
                <label>
                  Agendamento
                  <input name="scheduledOn" type="date" required />
                </label>
              </>
            ) : (
              <>
                <label>
                  Cliente homologado
                  <select name="merchantId" required>
                    <option value="">Selecione nesta página</option>
                    {merchants
                      .filter((m) => m.status === "APPROVED")
                      .map((m) => (
                        <option value={m.id} key={m.id}>
                          {m.name}
                        </option>
                      ))}
                  </select>
                </label>
                <label>
                  {modal === "payable"
                    ? "Descrição"
                    : "Referência do cliente final"}
                  <input
                    name={modal === "payable" ? "description" : "customerRef"}
                    required
                  />
                </label>
                <label>
                  Valor em reais
                  <input
                    name="amount"
                    required
                    inputMode="decimal"
                    placeholder="100,00"
                  />
                </label>
                <label>
                  Vencimento
                  <input name="dueDate" type="date" required />
                </label>
                {modal !== "payable" && (
                  <>
                    <label>
                      Meio
                      <select name="method">
                        <option>PIX</option>
                        <option>BOLETO</option>
                        <option>CREDIT_CARD</option>
                      </select>
                    </label>
                    <label>
                      Referência do contrato/mandato
                      <input name="reference" required />
                    </label>
                    {modal === "subscription" && (
                      <label>
                        Dia âncora
                        <input
                          name="anchorDay"
                          type="number"
                          min={1}
                          max={31}
                          required
                          defaultValue={10}
                        />
                      </label>
                    )}
                    <details>
                      <summary>Multa, juros e desconto</summary>
                      <p>
                        Taxas em basis points (100 = 1%). Juros/multa no cartão
                        ficam bloqueados até suporte contratual.
                      </p>
                      <label>
                        Multa (bps)
                        <input
                          name="fineBps"
                          type="number"
                          defaultValue={0}
                          min={0}
                          max={10000}
                        />
                      </label>
                      <label>
                        Juros diários (bps)
                        <input
                          name="dailyInterestBps"
                          type="number"
                          defaultValue={0}
                          min={0}
                          max={100}
                        />
                      </label>
                      <label>
                        Teto de acréscimos (bps)
                        <input
                          name="maxSurchargeBps"
                          type="number"
                          defaultValue={0}
                          min={0}
                          max={10000}
                        />
                      </label>
                      <label>
                        Desconto em reais
                        <input name="discount" inputMode="decimal" />
                      </label>
                      <label>
                        Desconto até
                        <input name="discountUntil" type="date" />
                      </label>
                    </details>
                  </>
                )}
              </>
            )}
            <button className="button primary" disabled={busy}>
              Registrar
            </button>
          </form>
        </Modal>
      )}
    </div>
  );
}
