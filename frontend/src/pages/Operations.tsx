import { useState } from "react";
import { Plus, Search, ArrowRight, Download } from "lucide-react";
import { catalog } from "../data/demo";
import { Badge, Empty, Heading, Metric, Modal, Notice } from "../components/UI";
import { api, download, money, type Session } from "../api";
import { assessDemo, splitAmount, toCents } from "../domain.mjs";

/** In-memory CRUD intentionally never advertises administrative backend persistence. */
export function Directory({
  kind,
  session,
}: {
  kind: keyof typeof catalog;
  session: Session;
}) {
  const definition = catalog[kind];
  const [rows, setRows] = useState(definition.rows);
  const [filter, setFilter] = useState("");
  const [edit, setEdit] = useState<number | null>(null);
  const [open, setOpen] = useState(false);
  function save(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const data = new FormData(event.currentTarget);
    const row = definition.columns.map((_, i) => String(data.get(String(i))));
    setRows((current) =>
      edit === null
        ? [...current, row]
        : current.map((item, i) => (i === edit ? row : item)),
    );
    setOpen(false);
  }
  return (
    <>
      <Heading title={definition.title} subtitle={definition.subtitle}>
        <button
          className="primary"
          disabled={session.mode !== "demo"}
          onClick={() => {
            setEdit(null);
            setOpen(true);
          }}
        >
          <Plus size={16} />
          Adicionar {definition.singular}
        </button>
      </Heading>
      <Notice>
        Área demonstrativa. Cadastros, permissões e alterações ficam somente
        nesta tela; não são aplicados à operação real.
      </Notice>
      {session.mode === "connected" ? (
        <Empty title="API administrativa ainda não disponível" />
      ) : (
        <section className="card">
          <div className="search">
            <Search size={17} />
            <input
              placeholder="Pesquisar registros"
              aria-label="Pesquisar registros"
              value={filter}
              onChange={(e) => setFilter(e.target.value)}
            />
          </div>
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  {definition.columns.map((c) => (
                    <th key={c}>{c}</th>
                  ))}
                  <th>Ações</th>
                </tr>
              </thead>
              <tbody>
                {rows
                  .map((row, index) => ({ row, index }))
                  .filter(({ row }) =>
                    row.join(" ").toLowerCase().includes(filter.toLowerCase()),
                  )
                  .map(({ row, index }) => (
                    <tr key={index}>
                      {row.map((value, i) => (
                        <td key={i}>
                          {i === 3 ? <Badge>{value}</Badge> : value}
                        </td>
                      ))}
                      <td>
                        <button
                          onClick={() => {
                            setEdit(index);
                            setOpen(true);
                          }}
                        >
                          Editar
                        </button>
                      </td>
                    </tr>
                  ))}
              </tbody>
            </table>
          </div>
        </section>
      )}
      {open && (
        <Modal
          title={`${edit === null ? "Adicionar" : "Editar"} ${definition.singular} demo`}
          onClose={() => setOpen(false)}
        >
          <form className="form" onSubmit={save}>
            {definition.columns.map((col, i) => (
              <label key={col}>
                {col}
                <input
                  required
                  name={String(i)}
                  maxLength={120}
                  defaultValue={edit === null ? "" : rows[edit][i]}
                  type={col === "E-mail" ? "email" : "text"}
                />
              </label>
            ))}
            <button className="primary">Salvar demonstração</button>
          </form>
        </Modal>
      )}
    </>
  );
}

export function Fraud({ session }: { session: Session }) {
  const [result, setResult] = useState<unknown>(null);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  async function evaluate(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setBusy(true);
    setError("");
    setResult(null);
    try {
      const f = new FormData(event.currentTarget);
      const mode = String(f.get("mode") || "INPAY_RISK");
      const typing = Number(f.get("typing") || 0);
      const pointer = Number(f.get("pointer") || 0);
      const dwell = Number(f.get("dwell") || 0);
      const body = {
        paymentId: String(f.get("id")),
        amount: toCents(String(f.get("amount"))),
        attemptsLastHour: Number(f.get("attempts")),
        declinesLastHour: Number(f.get("declines")),
        knownCompromised: f.get("compromised") === "on",
        behavior:
          typing > 0 || pointer > 0 || dwell > 0
            ? {
                typingIntervalMs: typing,
                mouseEntropy: pointer,
                navigationSeconds: dwell,
                unusualJourney: false,
              }
            : undefined,
      };
      if (session.mode !== "demo") {
        await api(session, "fraud/mode", { mode });
      }
      const demo =
        mode === "INPAY_RISK"
          ? {
              ...assessDemo(
                body.amount,
                body.attemptsLastHour,
                body.declinesLastHour,
                body.knownCompromised,
              ),
              provider: "INPAY_RISK",
              tokens: 90 + (body.behavior ? 180 : 0),
              policyVersion: "inpay-risk-v1",
            }
          : {
              provider: mode,
              score: body.knownCompromised ? 90 : 15,
              decision: body.knownCompromised ? "REJECT" : "ACCEPT",
              policyVersion:
                mode === "CLEARSALE" ? "clearsale-mock-v1" : "serasa-experian-mock-v1",
              reasons: ["MOCK_STUB"],
              tokens: 0,
            };
      setResult(session.mode === "demo" ? demo : await api(session, "fraud", body));
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }
  return (
    <>
      <Heading
        title="Antifraude"
        subtitle="Decisões explicáveis para proteger sua operação."
      />
      <div className="metrics">
        <Metric
          label="Motor nativo"
          value="InPay Risk"
          note="Score explicável e tokens de IA"
        />
        <Metric label="Aprovação" value="0–39" note="Score: ACCEPT" />
        <Metric label="Revisão" value="40–69" note="Score: REVIEW" />
        <Metric label="Rejeição" value="70–100" note="Score: REJECT" />
      </div>
      <div className="two-col">
        <section className="card">
          <h2>
            {session.mode === "demo"
              ? "Simular avaliação"
              : "Avaliar e persistir sinais"}
          </h2>
          <p>
            O gateway escolhe o modo nativo. InPay Risk analisa comportamento,
            dispositivo, cadastro e chargebacks e mede o custo em tokens. ClearSale
            e Serasa Experian respondem pelo stub enquanto a chamada real está
            desligada.
          </p>
          <form className="form" onSubmit={evaluate}>
            <label>
              Modo nativo
              <select name="mode" defaultValue="INPAY_RISK">
                <option value="INPAY_RISK">InPay Risk</option>
                <option value="CLEARSALE">ClearSale</option>
                <option value="SERASA_EXPERIAN">Serasa Experian</option>
              </select>
            </label>
            <label>
              UUID do pagamento
              <input
                name="id"
                required
                pattern="[0-9a-fA-F-]{36}"
                defaultValue="22222222-2222-2222-2222-222222222222"
              />
            </label>
            <label>
              Valor (R$)
              <input
                name="amount"
                required
                defaultValue="250,00"
                inputMode="decimal"
              />
            </label>
            <div className="form-grid">
              <label>
                Tentativas na última hora
                <input
                  name="attempts"
                  type="number"
                  min="0"
                  max="100000"
                  required
                  defaultValue="1"
                />
              </label>
              <label>
                Recusas na última hora
                <input
                  name="declines"
                  type="number"
                  min="0"
                  max="100000"
                  required
                  defaultValue="0"
                />
              </label>
            </div>
            <label className="check">
              <input name="compromised" type="checkbox" />
              Comprometimento conhecido
            </label>
            <div className="form-grid">
              <label>
                Intervalo de digitação (ms)
                <input name="typing" type="number" min="0" max="60000" defaultValue="0" />
              </label>
              <label>
                Movimento do ponteiro (0–100)
                <input name="pointer" type="number" min="0" max="100" defaultValue="0" />
              </label>
            </div>
            <label>
              Permanência no checkout (s)
              <input name="dwell" type="number" min="0" max="86400" defaultValue="0" />
            </label>
            {error && (
              <p role="alert" className="error">
                {error}
              </p>
            )}
            <button className="primary" disabled={busy}>
              {busy ? "Avaliando..." : "Avaliar risco"}
            </button>
          </form>
        </section>
        <section className="card">
          <h2>Regras e decisão</h2>
          <ul className="rule-list">
            <li>
              Comprometimento conhecido <strong>+70</strong>
            </li>
            <li>
              5 ou mais tentativas / hora <strong>+30</strong>
            </li>
            <li>
              3 ou mais recusas / hora <strong>+25</strong>
            </li>
            <li>
              Valor acima de R$ 1.000 <strong>+20</strong>
            </li>
          </ul>
          <Notice>
            InPay Risk soma sinais explicáveis e reserva tokens para o custo do
            modelo: 90 na síntese do score e 180 quando há comportamento. A
            franquia e o excedente estão nos planos. Chargeback confirmado sobe o
            score do dispositivo; contestação legítima reduz falso positivo.
            ClearSale e Serasa Experian, quando selecionados, não gastam token de
            IA.
          </Notice>
          {result !== null && (
            <pre aria-live="polite">{JSON.stringify(result, null, 2)}</pre>
          )}
        </section>
      </div>
    </>
  );
}

export function Split() {
  const [amount, setAmount] = useState("100,00");
  const [percent, setPercent] = useState(10);
  let result: number[] = [];
  let error = "";
  try {
    result = splitAmount(toCents(amount), percent);
  } catch (e) {
    error = (e as Error).message;
  }
  return (
    <>
      <Heading
        title="Split de pagamentos"
        subtitle="Planeje a divisão entre marketplace e recebedores."
      />
      <Notice>
        Simulador de divisão, sem liquidação ou cadastro no provedor. Split
        exige backend próprio e suporte contratual; não está integrado aos
        connectors atuais.
      </Notice>
      <div className="two-col">
        <section className="card form">
          <h2>Simular distribuição</h2>
          <label>
            Valor da venda (R$)
            <input
              value={amount}
              onChange={(e) => setAmount(e.target.value)}
              inputMode="decimal"
            />
          </label>
          <label>
            Participação do marketplace: {percent}%
            <input
              type="range"
              min="0"
              max="100"
              step="1"
              value={percent}
              onChange={(e) => setPercent(Number(e.target.value))}
            />
          </label>
          {error && <p className="error">{error}</p>}
          <p>
            O recebedor recebe o restante. O arredondamento preserva o total em
            centavos.
          </p>
        </section>
        <section className="card">
          <h2>Distribuição prevista</h2>
          <div className="split-bar">
            <i style={{ width: `${percent}%` }} />
          </div>
          <dl className="details">
            <dt>Marketplace</dt>
            <dd>{result.length ? money(result[0]) : "—"}</dd>
            <dt>Recebedor</dt>
            <dd>{result.length ? money(result[1]) : "—"}</dd>
          </dl>
          <button
            disabled={!result.length}
            onClick={() =>
              download(
                "inpay-split-simulacao.json",
                JSON.stringify(
                  {
                    simulation: true,
                    total: toCents(amount),
                    marketplace: result[0],
                    recipient: result[1],
                  },
                  null,
                  2,
                ),
                "application/json",
              )
            }
          >
            <Download size={16} />
            Exportar simulação
          </button>
        </section>
      </div>
    </>
  );
}

export function Journey() {
  const stages = [
    [
      "01",
      "Entrada",
      "Link de pagamento ou API REST",
      "Registro disponível; checkout público pendente",
    ],
    [
      "02",
      "Identidade e token",
      "Cognito, escopos e contexto do lojista",
      "Alias local disponível; vault real pendente",
    ],
    [
      "03",
      "Antifraude",
      "Análise de sinais e decisão explicável",
      "Avaliação persistente disponível",
    ],
    [
      "04",
      "Roteamento",
      "Seleção de afiliação e adquirente",
      "Política de domínio; orquestração pendente",
    ],
    [
      "05",
      "Processamento",
      "Autorizar, capturar, cancelar e consultar",
      "8 adaptadores HTTP; homologação externa pendente",
    ],
    [
      "06",
      "Pós-venda",
      "Callbacks, conciliação e notificações",
      "Integração ponta a ponta pendente",
    ],
  ];
  return (
    <>
      <Heading
        title="Jornada do pagamento"
        subtitle="Do pedido à conciliação, entenda cada etapa."
      />
      <Notice>
        Mapa de capacidades da implementação. Não representa monitoramento ao
        vivo de uma transação.
      </Notice>
      <div className="journey-grid">
        {stages.map(([n, title, description, status]) => (
          <section className="card journey-step" key={n}>
            <span>{n}</span>
            <ArrowRight size={20} />
            <h2>{title}</h2>
            <p>{description}</p>
            <small>{status}</small>
          </section>
        ))}
      </div>
      <section className="card">
        <h2>Quando o resultado é desconhecido</h2>
        <p>
          Timeout não significa recusa. Uma operação UNKNOWN deve ser consultada
          e conciliada antes de qualquer novo envio. O journal preserva a chave
          de idempotência; a recuperação automática ainda está pendente.
        </p>
      </section>
    </>
  );
}

export function Finance({ session }: { session: Session }) {
  const [tab, setTab] = useState("Recebíveis");
  return (
    <>
      <Heading
        title="Financeiro"
        subtitle="Recebíveis, liquidações e conciliação em um único espaço."
      />
      <div className="tabs">
        {["Recebíveis", "Extrato", "Conciliação", "Transferências"].map((t) => (
          <button
            className={t === tab ? "selected" : ""}
            onClick={() => setTab(t)}
            key={t}
          >
            {t}
          </button>
        ))}
      </div>
      <Notice>
        Valores ilustrativos. Não representam saldo bancário, dinheiro
        disponível ou agenda real de liquidação. APIs financeiras ainda
        pendentes.
      </Notice>
      {session.mode === "connected" ? (
        <Empty title="Dados financeiros ainda não integrados" />
      ) : (
        <>
          <div className="metrics three">
            <Metric
              label="Agenda demonstrativa"
              value="R$ 38.450,00"
              note="Valores de exemplo"
            />
            <Metric
              label="Liquidações demonstrativas"
              value="R$ 12.800,00"
              note="Sem conexão bancária"
            />
            <Metric
              label="Diferenças ilustrativas"
              value="R$ 124,50"
              note="2 itens para análise"
            />
          </div>
          <section className="card">
            <h2>{tab}</h2>
            <div className="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>Referência</th>
                    <th>Descrição</th>
                    <th>Valor</th>
                    <th>Situação</th>
                  </tr>
                </thead>
                <tbody>
                  {[
                    [
                      "demo_fin_001",
                      tab === "Conciliação"
                        ? "Diferença de taxa"
                        : "Lote de exemplo",
                      "R$ 124,50",
                      "Em análise",
                    ],
                    [
                      "demo_fin_002",
                      "Registro demonstrativo",
                      "R$ 2.400,00",
                      "Pendente",
                    ],
                  ].map((row) => (
                    <tr key={row[0]}>
                      {row.map((c, i) => (
                        <td key={i}>{i === 3 ? <Badge>{c}</Badge> : c}</td>
                      ))}
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </section>
        </>
      )}
    </>
  );
}

/** Produces a routing proposal only; the live routing service has no administration endpoint. */
export function Routing() {
  const [primary, setPrimary] = useState("Cielo");
  const [secondary, setSecondary] = useState("Rede");
  const [strategy, setStrategy] = useState("Prioridade fixa");
  const [message, setMessage] = useState("");
  const providers = [
    "Cielo",
    "Rede",
    "Getnet",
    "Stone",
    "Bin",
    "Global Payments",
    "PagBank",
    "Safrapay",
  ];
  return (
    <>
      <Heading
        title="Roteamento"
        subtitle="Planeje a seleção de adquirentes por operação."
      />
      <Notice>
        Proposta de configuração. A API administrativa de roteamento ainda não
        está disponível; nenhum tráfego é alterado por esta tela.
      </Notice>
      <div className="two-col">
        <section className="card">
          <h2>Política para cartões</h2>
          <form
            className="form"
            onSubmit={(event) => {
              event.preventDefault();
              if (primary === secondary) {
                setMessage("Escolha adquirentes diferentes.");
                return;
              }
              download(
                "inpay-routing-draft.json",
                JSON.stringify(
                  {
                    draft: true,
                    strategy,
                    primary,
                    secondary,
                    retryUnknown: false,
                    requiresAffiliationValidation: true,
                  },
                  null,
                  2,
                ),
                "application/json",
              );
              setMessage("Proposta exportada. Nenhuma rota foi ativada.");
            }}
          >
            <label>
              Estratégia proposta
              <select
                value={strategy}
                onChange={(e) => setStrategy(e.target.value)}
              >
                <option>Prioridade fixa</option>
                <option>Menor custo contratado</option>
                <option>Maior aprovação observada</option>
              </select>
            </label>
            <label>
              Adquirente principal
              <select
                value={primary}
                onChange={(e) => setPrimary(e.target.value)}
              >
                {providers.map((p) => (
                  <option key={p}>{p}</option>
                ))}
              </select>
            </label>
            <label>
              Adquirente alternativa
              <select
                value={secondary}
                onChange={(e) => setSecondary(e.target.value)}
              >
                {providers.map((p) => (
                  <option key={p}>{p}</option>
                ))}
              </select>
            </label>
            <button className="primary">Exportar proposta</button>
            {message && <p role="status">{message}</p>}
          </form>
        </section>
        <section className="card">
          <h2>Condições antes da ativação</h2>
          <ul className="rule-list">
            <li>Afiliação compatível com o lojista e a moeda.</li>
            <li>Token aceito pelo provedor de destino.</li>
            <li>Regras contratuais para bandeira, parcelas e captura.</li>
            <li>UNKNOWN bloqueia reenvio automático para outra adquirente.</li>
            <li>Custos e taxas de aprovação precisam de dados reais.</li>
          </ul>
          <Notice>
            Trocar de adquirente não transfere automaticamente a validade de um
            token. Uma recusa não deve produzir tentativas indiscriminadas.
          </Notice>
        </section>
      </div>
    </>
  );
}
