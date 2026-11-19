import { useState } from "react";
import {
  ArrowUpRight,
  Settings2,
  CreditCard,
  Landmark,
  QrCode,
  Download,
  FileJson,
} from "lucide-react";
import providers from "../data/providers.json";
import { Badge, Heading, Modal, Notice } from "../components/UI";
import { download } from "../api";
type Provider = (typeof providers)[number];

/** Provider drafts contain public settings and secret placeholders, never actual credentials. */
export default function Methods() {
  const [selected, setSelected] = useState<Provider | null>(null);
  const [values, setValues] = useState<Record<string, string>>({});
  const [environment, setEnvironment] = useState("sandbox");
  const [saved, setSaved] = useState<string[]>([]);
  const [message, setMessage] = useState("");
  function choose(provider: Provider, env = "sandbox") {
    setSelected(provider);
    setEnvironment(env);
    setMessage("");
    const production = provider.production as Record<
      string,
      string | undefined
    >;
    setValues(
      Object.fromEntries(
        provider.fields.map((f) => [
          f.key,
          f.secret
            ? ""
            : env === "production"
              ? (production[f.property] ?? f.value)
              : f.value,
        ]),
      ),
    );
  }
  function exportConfig(event: React.FormEvent) {
    event.preventDefault();
    if (!selected) return;
    const prefix = selected.id.toUpperCase().replaceAll("-", "_");
    const lines = [
      `# InPay / ${selected.name}: rascunho ${environment}; revise antes de implantar.`,
      `${prefix}_ENABLED=false`,
      ...selected.fields.map(
        (f) =>
          `${f.key}=${f.secret ? "<PREENCHER_NO_GERENCIADOR_DE_SEGREDOS>" : (values[f.key] ?? "")}`,
      ),
    ];
    download(
      `inpay-${selected.id}-${environment}.env.example`,
      lines.join("\n") + "\n",
    );
    setSaved((current) => [...new Set([...current, selected.id])]);
    setMessage(
      "Template exportado. Nenhuma configuração foi aplicada ao gateway.",
    );
  }
  return (
    <>
      <Heading
        title="Meios de pagamento"
        subtitle="Integrações separadas. Uma operação conectada."
      />
      <div className="method-summary">
        {[
          [CreditCard, "Cartão de crédito", "Núcleo transacional implementado"],
          [QrCode, "Pix", "Integração InPay pendente"],
          [Landmark, "Boleto", "Integração InPay pendente"],
        ].map(([Icon, title, sub]) => {
          const Glyph = Icon as typeof CreditCard;
          return (
            <article className="card" key={String(title)}>
              <Glyph size={24} />
              <h3>{String(title)}</h3>
              <p>{String(sub)}</p>
            </article>
          );
        })}
      </div>
      <Notice>
        As configurações abaixo geram templates para implantação. Credenciais
        reais ficam no servidor. Não há API administrativa para aplicar
        configurações ou testar afiliações pelo painel.
      </Notice>
      <div className="section-head">
        <div>
          <h2>Adquirentes e provedores</h2>
          <p>8 connectors · contratos e credenciais independentes</p>
        </div>
        <Badge>Configuração assistida</Badge>
      </div>
      <div className="provider-grid">
        {providers.map((p, i) => (
          <article className="card provider" key={p.id}>
            <div className="section-head">
              <div className={`provider-mark mark-${i}`}>
                {p.name.slice(0, 2)}
              </div>
              <Badge>
                {saved.includes(p.id) ? "Template exportado" : "Não verificado"}
              </Badge>
            </div>
            <h2>{p.name}</h2>
            <p>{p.product}</p>
            <div className="provider-auth">{p.auth}</div>
            <div className="provider-actions">
              <a href={p.docs} target="_blank" rel="noreferrer">
                Documentação <ArrowUpRight size={15} />
              </a>
              <button onClick={() => choose(p)}>
                <Settings2 size={15} />
                Configurar
              </button>
            </div>
          </article>
        ))}
      </div>
      {selected && (
        <Modal
          title={`Configurar ${selected.name}`}
          onClose={() => setSelected(null)}
        >
          <Notice>{selected.note}</Notice>
          <form className="form" onSubmit={exportConfig}>
            <label>
              Ambiente do template
              <select
                value={environment}
                onChange={(e) => choose(selected, e.target.value)}
              >
                <option value="sandbox">Sandbox / homologação</option>
                <option value="production">
                  Produção — requer homologação
                </option>
              </select>
            </label>
            <div className="form-grid">
              {selected.fields.map((f) => (
                <label key={f.key}>
                  {f.property}
                  <small className="field-key">{f.key}</small>
                  {f.secret ? (
                    <div className="secret-placeholder">
                      Definir no Secrets Manager / ambiente do servidor
                    </div>
                  ) : f.property === "allow-sensitive-card-data" ||
                    f.property === "brazil-enabled" ? (
                    <select
                      value={values[f.key]}
                      onChange={(e) =>
                        setValues({ ...values, [f.key]: e.target.value })
                      }
                    >
                      <option value="false">false</option>
                      <option value="true">
                        true — exige validação externa
                      </option>
                    </select>
                  ) : (
                    <input
                      value={values[f.key] ?? ""}
                      required={["tenant-id", "base-url"].includes(f.property)}
                      pattern={
                        f.property === "tenant-id"
                          ? "[0-9a-fA-F-]{36}"
                          : f.property.endsWith("timeout")
                            ? "[1-9][0-9]*(ms|s)"
                            : undefined
                      }
                      type={f.property.endsWith("url") ? "url" : "text"}
                      maxLength={500}
                      onChange={(e) =>
                        setValues({
                          ...values,
                          [f.key]: e.target.value.replace(/[\r\n]/g, ""),
                        })
                      }
                    />
                  )}
                </label>
              ))}
            </div>
            <Notice>
              Cartão no connector não implica Pix, boleto, split, recorrência ou
              3DS disponíveis na InPay. Essas capacidades dependem da
              implementação e da habilitação do contrato.
            </Notice>
            {message && (
              <p role="status" className="success">
                {message}
              </p>
            )}
            <div className="actions">
              <button
                type="button"
                onClick={() =>
                  download(
                    `${selected.id}-authorize.json`,
                    JSON.stringify(selected.example, null, 2),
                    "application/json",
                  )
                }
              >
                <FileJson size={16} />
                Exemplo de autorização
              </button>
              <button className="primary">
                <Download size={16} />
                Exportar template
              </button>
            </div>
          </form>
        </Modal>
      )}
    </>
  );
}
