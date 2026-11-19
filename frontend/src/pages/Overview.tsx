import { useState } from "react";
import {
  ArrowRight,
  Download,
  TrendingUp,
  ShieldCheck,
  CreditCard,
  Link2,
} from "lucide-react";
import { payments } from "../data/demo";
import { Badge, Empty, Heading, Metric } from "../components/UI";
import { download, money, type Session } from "../api";
import { csvCell } from "../domain.mjs";

/** All summary values derive from the same selected fixture window; no fabricated live totals. */
export default function Overview({
  session,
  navigate,
}: {
  session: Session;
  navigate: (page: string) => void;
}) {
  const [period, setPeriod] = useState(30);
  if (session.mode === "connected")
    return (
      <>
        <Heading
          title="Visão geral"
          subtitle="Acompanhe sua operação em um só lugar."
        />
        <Empty title="Indicadores aguardando integração">
          O backend ainda não oferece uma API de métricas agregadas. Consulte
          registros reais em Transações ou explore o modo demonstração.
        </Empty>
        <button className="primary" onClick={() => navigate("transactions")}>
          Consultar transação <ArrowRight size={16} />
        </button>
      </>
    );
  const rows = payments.filter((p) => p.day < period);
  const paid = rows.filter((p) => p.status === "Pago");
  const total = paid.reduce((sum, p) => sum + p.amount, 0);
  const values = Array.from({ length: 10 }, (_, index) =>
    paid
      .filter((p) => Math.floor((p.day / period) * 10) === index)
      .reduce((sum, p) => sum + p.amount, 0),
  );
  const max = Math.max(...values, 1);
  const cardShare =
    (rows.filter((p) => p.method === "Cartão").length / rows.length) * 100;
  const pixEnd =
    cardShare +
    (rows.filter((p) => p.method === "Pix").length / rows.length) * 100;
  const points = values
    .map((v, i) => `${i * 70 + 20},${190 - (v / max) * 140}`)
    .join(" ");
  return (
    <>
      <Heading
        title="Sua operação, em perspectiva."
        subtitle="Clareza em cada transação. Controle em cada decisão."
      >
        <select
          aria-label="Período"
          value={period}
          onChange={(e) => setPeriod(Number(e.target.value))}
        >
          <option value={30}>Últimos 30 dias</option>
          <option value={7}>Últimos 7 dias</option>
        </select>
        <button
          onClick={() =>
            download(
              "inpay-demonstracao.csv",
              [
                ["ID", "Valor em centavos", "Status"],
                ...rows.map((p) => [p.id, p.amount, p.status]),
              ]
                .map((r) => r.map(csvCell).join(";"))
                .join("\r\n"),
              "text/csv;charset=utf-8",
            )
          }
        >
          <Download size={16} /> Exportar
        </button>
      </Heading>
      <div className="metrics">
        <Metric
          label="Volume aprovado"
          value={money(total)}
          note={`${paid.length} pagamentos na amostra`}
        />
        <Metric
          label="Taxa de aprovação"
          value={`${((paid.length / rows.length) * 100).toFixed(1)}%`}
          note={`${rows.length} tentativas no período`}
        />
        <Metric
          label="Ticket médio"
          value={money(Math.round(total / paid.length))}
          note="Sobre pagamentos aprovados"
        />
        <Metric
          label="Em análise"
          value={String(
            rows.filter((p) => p.status === "Em análise").length,
          ).padStart(2, "0")}
          note="Casos ilustrativos de revisão"
        />
      </div>
      <div className="overview-grid">
        <section className="card volume">
          <div className="section-head">
            <div>
              <h2>Volume de pagamentos</h2>
              <p>Valores aprovados no período selecionado</p>
            </div>
            <span className="chart-key">● Volume aprovado</span>
          </div>
          <div className="chart">
            <div className="chart-y">
              <span>{money(max)}</span>
              <span>{money(Math.round(max / 2))}</span>
              <span>R$ 0</span>
            </div>
            <svg
              viewBox="0 0 680 220"
              role="img"
              aria-label="Evolução do volume aprovado em dez intervalos"
            >
              <defs>
                <linearGradient id="area" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="0%" stopColor="#1a9c8c" stopOpacity=".22" />
                  <stop offset="100%" stopColor="#1a9c8c" stopOpacity="0" />
                </linearGradient>
              </defs>
              {[50, 120, 190].map((y) => (
                <line
                  key={y}
                  x1="20"
                  y1={y}
                  x2="650"
                  y2={y}
                  stroke="#e7edf0"
                  strokeDasharray="4 5"
                />
              ))}
              <polygon points={`20,210 ${points} 650,210`} fill="url(#area)" />
              <polyline
                points={points}
                fill="none"
                stroke="#158a7e"
                strokeWidth="3"
                strokeLinejoin="round"
              />
              {values.map((v, i) => (
                <circle
                  key={i}
                  cx={i * 70 + 20}
                  cy={190 - (v / max) * 140}
                  r="4"
                  fill="#fff"
                  stroke="#158a7e"
                  strokeWidth="2"
                >
                  <title>{money(v)}</title>
                </circle>
              ))}
            </svg>
          </div>
          <div className="chart-labels">
            <span>Início do período</span>
            <span>Distribuição em 10 intervalos</span>
            <span>Fim do período</span>
          </div>
        </section>
        <section className="card">
          <h2>Meios de pagamento</h2>
          <p>Distribuição das tentativas</p>
          <div
            className="donut"
            style={{
              background: `conic-gradient(#178e82 0% ${cardShare}%,#80c9bc ${cardShare}% ${pixEnd}%,#b6bee8 ${pixEnd}%)`,
            }}
            aria-label="Distribuição dos meios de pagamento; percentuais na legenda"
          >
            <div>
              <strong>{rows.length}</strong>
              <small>transações</small>
            </div>
          </div>
          {["Cartão", "Pix", "Boleto"].map((method, i) => (
            <div className="legend" key={method}>
              <span>
                <i
                  style={{ background: ["#178e82", "#80c9bc", "#b6bee8"][i] }}
                />
                {method}
              </span>
              <strong>
                {Math.round(
                  (rows.filter((p) => p.method === method).length /
                    rows.length) *
                    100,
                )}
                %
              </strong>
            </div>
          ))}
        </section>
      </div>
      <div className="journey-banner">
        <div className="journey-icon">
          <TrendingUp />
        </div>
        <div>
          <h3>Uma jornada conectada, do início ao fim.</h3>
          <p>Do checkout à conciliação, entenda o caminho de cada pagamento.</p>
        </div>
        <button onClick={() => navigate("journey")}>
          Explorar jornada <ArrowRight size={17} />
        </button>
      </div>
      <div className="overview-grid">
        <section className="card">
          <div className="section-head">
            <h2>Transações recentes</h2>
            <button
              className="text-button"
              onClick={() => navigate("transactions")}
            >
              Ver todas <ArrowRight size={16} />
            </button>
          </div>
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>Cliente / transação</th>
                  <th>Meio</th>
                  <th>Valor</th>
                  <th>Status</th>
                </tr>
              </thead>
              <tbody>
                {rows.slice(0, 5).map((p) => (
                  <tr key={p.id}>
                    <td>
                      <strong>{p.customer}</strong>
                      <small>{p.id}</small>
                    </td>
                    <td>{p.method}</td>
                    <td>{money(p.amount)}</td>
                    <td>
                      <Badge>{p.status}</Badge>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </section>
        <section className="card quick">
          <h2>Seu próximo passo</h2>
          <p>Ferramentas para sua operação</p>
          {[
            [Link2, "Criar um link", "links"],
            [CreditCard, "Configurar adquirentes", "methods"],
            [ShieldCheck, "Explorar antifraude", "fraud"],
          ].map(([Icon, label, page]) => {
            const Glyph = Icon as typeof Link2;
            return (
              <button key={String(page)} onClick={() => navigate(String(page))}>
                <Glyph size={20} />
                <span>{String(label)}</span>
                <ArrowRight size={16} />
              </button>
            );
          })}
          <div className="tip">
            Ambiente de demonstração
            <br />
            <small>Explore sem movimentar dinheiro.</small>
          </div>
        </section>
      </div>
    </>
  );
}
