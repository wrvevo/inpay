import { useState } from "react";
import { Plus, Search } from "lucide-react";
import { Badge, Heading, Modal, Notice, Empty } from "../components/UI";
import { api, money, type Session } from "../api";
import { toCents } from "../domain.mjs";
type Link = {
  id: string;
  orderId: string;
  amount: number;
  expiresAt: string;
  status: string;
  revoked?: boolean;
  publicCode?: string;
};

/** Connected links stay in memory and are never mixed with fixture rows. */
export default function Links({ session }: { session: Session }) {
  const [links, setLinks] = useState<Link[]>(
    session.mode === "demo"
      ? [
          {
            id: "demo_link_01",
            orderId: "Consultoria · Novembro",
            amount: 150000,
            expiresAt: "2027-01-01T23:59:59Z",
            status: "ACTIVE",
          },
        ]
      : [],
  );
  const [open, setOpen] = useState(false);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const [lookup, setLookup] = useState("");
  const [revoke, setRevoke] = useState<Link | null>(null);
  async function create(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setBusy(true);
    setError("");
    try {
      const data = new FormData(event.currentTarget);
      const amount = toCents(String(data.get("amount")));
      const expiresAt = new Date(String(data.get("expiry"))).toISOString();
      if (Date.parse(expiresAt) <= Date.now())
        throw new Error("Escolha uma data futura.");
      const orderId = String(data.get("order"));
      const link =
        session.mode === "demo"
          ? {
              id: `demo_${crypto.randomUUID()}`,
              orderId,
              amount,
              expiresAt,
              status: "ACTIVE",
            }
          : await api(session, "links", {
              merchantOrderId: orderId,
              amount,
              expiresAt,
            });
      setLinks((current) => [
        {
          ...link,
          status: link.revoked
            ? "REVOKED"
            : Date.parse(link.expiresAt) <= Date.now()
              ? "EXPIRED"
              : "ACTIVE",
        },
        ...current.filter((l) => l.id !== link.id),
      ]);
      setOpen(false);
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }
  async function find() {
    setBusy(true);
    setError("");
    try {
      const link = await api(session, `links/${encodeURIComponent(lookup)}`);
      setLinks((current) => [
        {
          ...link,
          status: link.revoked
            ? "REVOKED"
            : Date.parse(link.expiresAt) <= Date.now()
              ? "EXPIRED"
              : "ACTIVE",
        },
        ...current.filter((l) => l.id !== link.id),
      ]);
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }
  async function revokeLink() {
    if (!revoke) return;
    setBusy(true);
    setError("");
    try {
      if (session.mode === "connected")
        await api(session, `links/${revoke.id}/revoke`, {});
      setLinks((current) =>
        current.map((l) =>
          l.id === revoke.id ? { ...l, status: "REVOKED" } : l,
        ),
      );
      setRevoke(null);
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }
  return (
    <>
      <Heading
        title="Links de pagamento"
        subtitle="Organize cobranças com valor e validade definidos."
      >
        <button
          className="primary"
          onClick={() => {
            setError("");
            setOpen(true);
          }}
        >
          <Plus size={17} />
          Novo link
        </button>
      </Heading>
      <Notice>
        O serviço cria registros de links. O checkout público ainda não foi
        implementado; por isso, não há URL de pagamento para compartilhar.
      </Notice>
      {error && !open && (
        <p className="error" role="alert">
          {error}
        </p>
      )}
      <section className="card">
        {session.mode === "connected" && (
          <div className="filters">
            <div className="search">
              <Search size={18} />
              <input
                aria-label="UUID do link"
                placeholder="Consultar UUID do link"
                value={lookup}
                onChange={(e) => setLookup(e.target.value)}
              />
            </div>
            <button
              onClick={find}
              disabled={busy || !/^[\da-f-]{36}$/i.test(lookup)}
            >
              Consultar
            </button>
          </div>
        )}
        {links.length ? (
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>Referência</th>
                  <th>Valor</th>
                  <th>Validade</th>
                  <th>Status</th>
                  <th>Ações</th>
                </tr>
              </thead>
              <tbody>
                {links.map((l) => (
                  <tr key={l.id}>
                    <td>
                      <strong>{l.orderId}</strong>
                      <small>{l.id}</small>
                    </td>
                    <td>{money(l.amount)}</td>
                    <td>{new Date(l.expiresAt).toLocaleString("pt-BR")}</td>
                    <td>
                      <Badge>{l.status}</Badge>
                    </td>
                    <td>
                      <button
                        disabled={l.status !== "ACTIVE"}
                        onClick={() => setRevoke(l)}
                      >
                        Revogar
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : (
          <Empty title="Nenhum link nesta sessão">
            Crie ou consulte um registro. O backend ainda não disponibiliza
            listagem de links.
          </Empty>
        )}
      </section>
      {open && (
        <Modal
          title={
            session.mode === "demo"
              ? "Criar link demonstrativo"
              : "Criar registro de link"
          }
          onClose={() => setOpen(false)}
        >
          <form onSubmit={create} className="form">
            <label>
              Referência do pedido
              <input
                name="order"
                required
                maxLength={100}
                placeholder="Ex.: Consultoria de novembro"
              />
            </label>
            <div className="form-grid">
              <label>
                Valor (R$)
                <input
                  name="amount"
                  required
                  inputMode="decimal"
                  placeholder="150,00"
                />
              </label>
              <label>
                Validade
                <input name="expiry" required type="datetime-local" />
              </label>
            </div>
            {error && (
              <p className="error" role="alert">
                {error}
              </p>
            )}
            <button className="primary" disabled={busy}>
              {busy ? "Criando..." : "Criar registro"}
            </button>
          </form>
        </Modal>
      )}
      {revoke && (
        <Modal title="Revogar link" onClose={() => setRevoke(null)}>
          <p>
            O registro “{revoke.orderId}” deixará de aceitar novas reservas.
            Deseja continuar?
          </p>
          <button className="primary" disabled={busy} onClick={revokeLink}>
            {busy ? "Revogando..." : "Confirmar revogação"}
          </button>
        </Modal>
      )}
    </>
  );
}
