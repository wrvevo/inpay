import { useEffect, useState, useRef } from "react";
import { api, type Session } from "../api";
import { Notice } from "../components/UI";
const names = [
  "C6 Bank",
  "Itaú",
  "Santander",
  "Bradesco",
  "Inter",
  "Nubank",
  "Banco PAN",
  "Banco do Brasil",
  "Caixa",
  "BTG Pactual",
  "Sicoob",
  "Sicredi",
];
type Bank = { id: string; name: string; status: string };
/** Referências de configuração não habilitam funcionalidades ainda não implementadas. */
export default function BankConnections({ session }: { session: Session }) {
  const [banks, setBanks] = useState<Bank[]>([]),
    [message, setMessage] = useState(""),
    [busy, setBusy] = useState(false);
  const key = useRef(crypto.randomUUID());
  const submittedBody = useRef("");
  useEffect(() => {
    let active = true;
    if (session.mode !== "demo")
      api(session, "bank-config/banks")
        .then((x) => {
          if (active) setBanks(x);
        })
        .catch((e) => {
          if (active) setMessage(e.message);
        });
    return () => {
      active = false;
    };
  }, [session]);
  async function submit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (busy) return;
    setBusy(true);
    const form = new FormData(event.currentTarget);
    const body = JSON.stringify(Object.fromEntries(form));
    if (submittedBody.current && submittedBody.current !== body)
      key.current = crypto.randomUUID();
    submittedBody.current = body;
    try {
      const response = await fetch("/api/bank-config/connections", {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          "Idempotency-Key": key.current,
          "X-Merchant-Id": session.tenant,
          ...(session.token
            ? { Authorization: `Bearer ${session.token}` }
            : {}),
        },
        body,
        signal: AbortSignal.timeout(15000),
      });
      if (!response.ok)
        throw new Error(`Não foi possível registrar (HTTP ${response.status})`);
      key.current = crypto.randomUUID();
      setMessage(
        "Configuração registrada. Integração pendente de implementação e homologação.",
      );
    } catch (e) {
      setMessage((e as Error).message);
    } finally {
      setBusy(false);
    }
  }
  return (
    <section className="panel" style={{ padding: 24, marginTop: 24 }}>
      <h3>Conexões com instituições financeiras</h3>
      <Notice>
        Catálogo de preparação. Os adaptadores bancários abaixo ainda não
        executam operações. Credenciais e certificados devem ficar no
        gerenciador de segredos.
      </Notice>
      <div className="table-wrap">
        <table>
          <thead>
            <tr>
              <th>Instituição</th>
              <th>Situação</th>
            </tr>
          </thead>
          <tbody>
            {(session.mode === "demo"
              ? names.map((name, i) => ({
                  id: String(i),
                  name,
                  status: "NOT_IMPLEMENTED",
                }))
              : banks
            ).map((bank) => (
              <tr key={bank.id}>
                <td>{bank.name}</td>
                <td>Pendente de integração e homologação</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      {message && <Notice>{message}</Notice>}
      <form className="form-grid" onSubmit={submit}>
        <label>
          Instituição
          <select name="bank" required>
            {banks.map((bank) => (
              <option key={bank.id} value={bank.id}>
                {bank.name}
              </option>
            ))}
          </select>
        </label>
        <label>
          Ambiente
          <select name="environment">
            <option value="sandbox">Homologação</option>
            <option value="production">
              Produção (permanece desabilitada)
            </option>
          </select>
        </label>
        <label>
          Referência das credenciais
          <input
            name="credentialReference"
            required
            maxLength={256}
            placeholder="secrets/inpay/tenant/banco"
          />
        </label>
        <label>
          Referência do certificado
          <input
            name="certificateReference"
            required
            maxLength={256}
            placeholder="secrets/inpay/tenant/certificado"
          />
        </label>
        <button
          className="button primary"
          disabled={session.mode === "demo" || busy || !banks.length}
        >
          Registrar configuração pendente
        </button>
      </form>
    </section>
  );
}
