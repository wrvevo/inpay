import { useState } from "react";
import { Heading, Notice } from "../components/UI";

const riskDefaults = { review: 40, reject: 70, mode: "INPAY_RISK" };
const providerDefaults = {
  clearsale: { enabled: false, mock: true, sellerCode: "loja-lab" },
  serasa: { enabled: false, mock: true, contractCode: "contrato-lab" },
};

export function RiskDesk() {
  const [config, setConfig] = useState(riskDefaults);
  return (
    <>
      <Heading title="Gestão InRisk" subtitle="Limiares, modo nativo e leitura do score." />
      <div className="metrics">
        <section className="card"><h2>Aprovação</h2><strong>0–{config.review - 1}</strong></section>
        <section className="card"><h2>Revisão</h2><strong>{config.review}–{config.reject - 1}</strong></section>
        <section className="card"><h2>Rejeição</h2><strong>{config.reject}–100</strong></section>
      </div>
      <section className="card">
        <form
          className="form"
          onSubmit={(event) => {
            event.preventDefault();
            const form = new FormData(event.currentTarget);
            setConfig({
              review: Number(form.get("review")),
              reject: Number(form.get("reject")),
              mode: String(form.get("mode")),
            });
          }}
        >
          <label>
            Modo nativo
            <select name="mode" defaultValue={config.mode}>
              <option value="INPAY_RISK">InPay Risk</option>
              <option value="CLEARSALE">ClearSale</option>
              <option value="SERASA_EXPERIAN">Serasa Experian</option>
            </select>
          </label>
          <label>
            Score mínimo de revisão
            <input name="review" type="number" min={1} max={99} defaultValue={config.review} />
          </label>
          <label>
            Score mínimo de rejeição
            <input name="reject" type="number" min={1} max={100} defaultValue={config.reject} />
          </label>
          <button className="primary">Salvar gestão</button>
        </form>
        <Notice>
          O score continua explicável. Tokens de IA só entram quando o modo é InPay Risk. ClearSale
          e Serasa seguem o stub enquanto a chamada real está desligada.
        </Notice>
      </section>
    </>
  );
}

export function ProviderSetup({ provider }: { provider: "clearsale" | "serasa" }) {
  const initial = providerDefaults[provider];
  const [saved, setSaved] = useState(initial);
  const title = provider === "clearsale" ? "ClearSale" : "Serasa Experian";
  return (
    <>
      <Heading title={title} subtitle="Configuração do conector de antifraude desta loja." />
      <section className="card">
        <form
          className="form"
          onSubmit={(event) => {
            event.preventDefault();
            const form = new FormData(event.currentTarget);
            setSaved({
              enabled: form.get("enabled") === "on",
              mock: true,
              sellerCode: String(form.get("code") || ""),
              contractCode: String(form.get("code") || ""),
            });
          }}
        >
          <label className="check">
            <input name="enabled" type="checkbox" defaultChecked={saved.enabled} />
            Conector habilitado para a loja selecionada
          </label>
          <label>
            {provider === "clearsale" ? "Código do estabelecimento" : "Código do contrato"}
            <input name="code" required defaultValue={provider === "clearsale" ? saved.sellerCode : saved.contractCode} />
          </label>
          <p>X-Mock permanece ligado. A chamada real continua desabilitada.</p>
          {saved.enabled && <Notice>Configuração salva neste navegador para a sessão de laboratório.</Notice>}
          <button className="primary">Salvar {title}</button>
        </form>
      </section>
    </>
  );
}
