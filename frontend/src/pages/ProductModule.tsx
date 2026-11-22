import BankConnections from "./BankConnections";
import { useEffect, useState } from "react";
import { api, type Session } from "../api";
import { Notice } from "../components/UI";

type Props = { session: Session; product: "accounts" | "marketplace" };

/** Product boundaries remain visible even when the banking provider is unavailable. */
export default function ProductModule({ session, product }: Props) {
  const [data, setData] = useState<unknown[]>([]);
  const [error, setError] = useState("");
  const [section, setSection] = useState("recipients");
  const [account, setAccount] = useState("");
  const [result, setResult] = useState<unknown>();
  useEffect(() => {
    let active = true;
    setData([]);
    setError("");
    if (product === "marketplace" && session.mode !== "demo") {
      api(session, `commerce/${section}`)
        .then((response) => {
          if (active) setData(response.data);
        })
        .catch((e: Error) => {
          if (active) setError(e.message);
        });
    }
    return () => {
      active = false;
    };
  }, [product, section, session]);
  async function balance() {
    setError("");
    setResult(undefined);
    try {
      setResult(
        await api(session, `accounts/${encodeURIComponent(account)}/balance`),
      );
    } catch (e) {
      setError((e as Error).message);
    }
  }
  return (
    <div className="marketplace-page">
      <h2>INPAY {product === "accounts" ? "Accounts" : "Marketplace"}</h2>
      <Notice>
        {product === "accounts"
          ? "Contas PF/PJ, saldo bancário, extrato e Pix pertencem à instituição parceira. O conector Banking ainda não foi contratado/configurado; as APIs retornam indisponibilidade."
          : "Vendedores, planos de split e conciliação comercial. Planos cadastrados não comprovam liquidação e não liberam créditos ou repasses automaticamente."}
      </Notice>
      {session.mode === "demo" && (
        <Notice>
          Modo demonstrativo: nenhum dado financeiro real é carregado.
        </Notice>
      )}
      {error && <Notice>{error}</Notice>}
      {product === "accounts" && <BankConnections session={session} />}
      {product === "marketplace" ? (
        <>
          <label>
            Consulta{" "}
            <select
              value={section}
              onChange={(e) => setSection(e.target.value)}
            >
              <option value="recipients">Vendedores</option>
              <option value="split-plans">Planos de split</option>
              <option value="settlements">Conciliações</option>
            </select>
          </label>
          {data.length ? (
            <pre>{JSON.stringify(data, null, 2)}</pre>
          ) : (
            <p>
              Nenhum registro carregado. Cadastros e operações estão disponíveis
              na API documentada.
            </p>
          )}
          <p>
            Swagger local:{" "}
            <a
              href="http://localhost:8112/swagger-ui/index.html"
              target="_blank"
              rel="noreferrer"
            >
              Marketplace
            </a>
          </p>
        </>
      ) : (
        <>
          <label>
            Identificador da conta{" "}
            <input
              value={account}
              onChange={(e) => setAccount(e.target.value)}
            />
          </label>
          <button
            className="button"
            disabled={!account || session.mode === "demo"}
            onClick={() => void balance()}
          >
            Consultar saldo no parceiro
          </button>
          {result !== undefined && <pre>{JSON.stringify(result, null, 2)}</pre>}
          <p>
            Swagger local:{" "}
            <a
              href="http://localhost:8111/swagger-ui/index.html"
              target="_blank"
              rel="noreferrer"
            >
              Accounts
            </a>
          </p>
        </>
      )}
    </div>
  );
}
