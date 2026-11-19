import { useState } from "react";
import { Search, Download, ArrowUpRight } from "lucide-react";
import { payments, type Payment } from "../data/demo";
import { Badge, Empty, Heading, Modal, Notice } from "../components/UI";
import { api, download, money, type Session } from "../api";
import { csvCell } from "../domain.mjs";

export default function Transactions({ session }: { session: Session }) {
  const [search, setSearch] = useState("");
  const [status, setStatus] = useState("Todos");
  const [method, setMethod] = useState("Todos");
  const [page, setPage] = useState(0);
  const [selected, setSelected] = useState<Payment | null>(null);
  const [result, setResult] = useState<unknown>(null);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const rows = payments.filter(
    (p) =>
      `${p.id} ${p.customer}`.toLowerCase().includes(search.toLowerCase()) &&
      (status === "Todos" || p.status === status) &&
      (method === "Todos" || p.method === method),
  );
  async function lookup() {
    setBusy(true);
    setError("");
    setResult(null);
    try {
      setResult(
        await api(session, `transactions/${encodeURIComponent(search)}`),
      );
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }
  return (
    <>
      <Heading
        title="Transações"
        subtitle="Cada pagamento, com todos os seus detalhes."
      >
        <button
          disabled={session.mode !== "demo"}
          onClick={() =>
            download(
              "inpay-transacoes-demo.csv",
              [
                ["ID", "Cliente", "Valor em centavos", "Meio", "Status"],
                ...rows.map((p) => [
                  p.id,
                  p.customer,
                  p.amount,
                  p.method,
                  p.status,
                ]),
              ]
                .map((row) => row.map(csvCell).join(";"))
                .join("\r\n"),
              "text/csv;charset=utf-8",
            )
          }
        >
          <Download size={16} />
          Exportar CSV
        </button>
      </Heading>
      <section className="card">
        <div className="filters">
          <div className="search">
            <Search size={18} />
            <input
              aria-label="Buscar transações"
              placeholder={
                session.mode === "demo"
                  ? "Busque por cliente ou transação..."
                  : "UUID da transação"
              }
              value={search}
              onChange={(e) => {
                setSearch(e.target.value);
                setPage(0);
              }}
            />
          </div>
          {session.mode === "demo" ? (
            <>
              <select
                aria-label="Status"
                value={status}
                onChange={(e) => {
                  setStatus(e.target.value);
                  setPage(0);
                }}
              >
                {["Todos", "Pago", "Em análise", "Recusado", "Estornado"].map(
                  (v) => (
                    <option key={v}>{v}</option>
                  ),
                )}
              </select>
              <select
                aria-label="Meio"
                value={method}
                onChange={(e) => {
                  setMethod(e.target.value);
                  setPage(0);
                }}
              >
                {["Todos", "Cartão", "Pix", "Boleto"].map((v) => (
                  <option key={v}>{v}</option>
                ))}
              </select>
            </>
          ) : (
            <button
              className="primary"
              onClick={lookup}
              disabled={busy || !/^[\da-f-]{36}$/i.test(search)}
            >
              {busy ? "Consultando..." : "Consultar"}
            </button>
          )}
        </div>
        {error && (
          <p role="alert" className="error">
            {error}
          </p>
        )}
        {session.mode === "connected" ? (
          <>
            <Notice>
              A consulta real usa a projeção de transações. Listagem paginada e
              histórico completo ainda não estão disponíveis.
            </Notice>
            {result ? (
              <pre>{JSON.stringify(result, null, 2)}</pre>
            ) : (
              <Empty title="Consulte uma transação pelo UUID" />
            )}
          </>
        ) : rows.length ? (
          <>
            <div className="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>Cliente / ID</th>
                    <th>Valor</th>
                    <th>Meio / canal</th>
                    <th>Adquirente</th>
                    <th>Status</th>
                    <th>Detalhes</th>
                  </tr>
                </thead>
                <tbody>
                  {rows.slice(page * 10, page * 10 + 10).map((p) => (
                    <tr key={p.id}>
                      <td>
                        <strong>{p.customer}</strong>
                        <small>{p.id}</small>
                      </td>
                      <td>{money(p.amount)}</td>
                      <td>
                        {p.method}
                        <small>{p.channel}</small>
                      </td>
                      <td>{p.provider}</td>
                      <td>
                        <Badge>{p.status}</Badge>
                      </td>
                      <td>
                        <button
                          className="icon-button"
                          aria-label={`Detalhes ${p.id}`}
                          onClick={() => setSelected(p)}
                        >
                          <ArrowUpRight size={18} />
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <div className="pagination">
              <span>
                {rows.length} transações · página {page + 1}
              </span>
              <div>
                <button disabled={!page} onClick={() => setPage(page - 1)}>
                  Anterior
                </button>
                <button
                  disabled={(page + 1) * 10 >= rows.length}
                  onClick={() => setPage(page + 1)}
                >
                  Próxima
                </button>
              </div>
            </div>
          </>
        ) : (
          <Empty />
        )}
      </section>
      {selected && (
        <Modal
          title={`Transação ${selected.id}`}
          onClose={() => setSelected(null)}
        >
          <div className="detail-value">
            {money(selected.amount)} <Badge>{selected.status}</Badge>
          </div>
          <dl className="details">
            <dt>Cliente</dt>
            <dd>{selected.customer}</dd>
            <dt>Adquirente</dt>
            <dd>{selected.provider}</dd>
            <dt>Origem</dt>
            <dd>{selected.channel}</dd>
            <dt>Meio</dt>
            <dd>{selected.method}</dd>
          </dl>
          <h3>Histórico ilustrativo</h3>
          <ol className="timeline">
            <li>
              Pedido recebido <small>Identificador e valor validados</small>
            </li>
            <li>
              Avaliação de risco{" "}
              <small>Regras do cenário de demonstração</small>
            </li>
            <li>
              {selected.status}
              <small>Resultado ilustrativo da operação</small>
            </li>
          </ol>
          <Notice>
            Captura, estorno e cancelamento pela saga do Core ainda não estão
            disponíveis neste painel. Nenhuma cobrança é efetuada na
            demonstração.
          </Notice>
        </Modal>
      )}
    </>
  );
}
