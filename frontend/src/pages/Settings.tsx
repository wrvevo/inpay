import { useState } from "react";
import { Code2, ExternalLink, KeyRound, Bell } from "lucide-react";
import { Heading, Notice } from "../components/UI";
import { download, type Session } from "../api";

export function Settings({
  session,
  onSession,
}: {
  session: Session;
  onSession: (session: Session) => void;
}) {
  const [token, setToken] = useState("");
  const [tenant, setTenant] = useState(session.tenant);
  const [message, setMessage] = useState("");
  return (
    <>
      <Heading
        title="Configurações"
        subtitle="Identidade, ambiente e preferências do painel."
      />
      <div className="two-col">
        <section className="card">
          <KeyRound className="accent" />
          <h2>Conectar às APIs InPay</h2>
          <p>
            Use um access token Cognito emitido para um client autorizado e
            vinculado ao lojista. Não informe client secrets.
          </p>
          <form
            className="form"
            onSubmit={(e) => {
              e.preventDefault();
              onSession({ mode: "connected", token, tenant });
              setToken("");
              setMessage(
                "Modo conectado selecionado. A autorização será validada pelo serviço em cada operação.",
              );
            }}
          >
            <label>
              UUID do lojista
              <input
                required
                value={tenant}
                pattern="[0-9a-fA-F-]{36}"
                onChange={(e) => setTenant(e.target.value)}
              />
            </label>
            <label>
              Access token Cognito
              <input
                type="password"
                autoComplete="off"
                placeholder="Somente access token; não é persistido"
                value={token}
                onChange={(e) => setToken(e.target.value)}
              />
            </label>
            <Notice>
              No Docker local, o proxy utiliza a credencial de desenvolvimento
              configurada no servidor. Fora do modo local, o token é obrigatório
              para acessar dados.
            </Notice>
            <button className="primary">Usar modo conectado</button>
          </form>
          {message && <p role="status">{message}</p>}
          <button
            onClick={() => {
              onSession({
                mode: "demo",
                token: "",
                tenant: "11111111-1111-1111-1111-111111111111",
              });
              setMessage("Sessão encerrada e token removido da memória.");
            }}
          >
            Encerrar sessão / demonstração
          </button>
        </section>
        <section className="card">
          <h2>Segurança e acesso</h2>
          <dl className="details">
            <dt>Modo atual</dt>
            <dd>
              {session.mode === "demo" ? "Demonstração" : "Conectado às APIs"}
            </dd>
            <dt>Token</dt>
            <dd>
              {session.token ? "Em memória nesta sessão" : "Não informado"}
            </dd>
            <dt>Permissões</dt>
            <dd>Escopos e ownership nos serviços</dd>
            <dt>Credenciais de adquirentes</dt>
            <dd>Somente no ambiente dos connectors</dd>
          </dl>
          <Notice>
            O login interativo Cognito com Authorization Code + PKCE e a gestão
            de usuários ainda precisam ser integrados. Esta conexão técnica não
            provisiona usuários ou permissões.
          </Notice>
          <h3>Preferências</h3>
          <p>
            Moeda: BRL · Idioma: Português do Brasil
            <br />
            Datas exibidas no fuso horário do navegador.
          </p>
        </section>
      </div>
    </>
  );
}
export function Developers() {
  const [tab, setTab] = useState("API REST");
  return (
    <>
      <Heading
        title="Desenvolvedores"
        subtitle="Integrações com contratos claros e rastreabilidade."
      />
      <div className="tabs">
        {["API REST", "Webhooks", "Eventos e auditoria"].map((t) => (
          <button
            className={tab === t ? "selected" : ""}
            key={t}
            onClick={() => setTab(t)}
          >
            {t}
          </button>
        ))}
      </div>
      {tab === "API REST" ? (
        <div className="two-col">
          <section className="card">
            <Code2 className="accent" />
            <h2>Comece pela API</h2>
            <p>
              Exemplo de criação de registro de link. Não é uma cobrança nem um
              checkout público.
            </p>
            <pre>{`POST /v1/payment-links\nAuthorization: Bearer <ACCESS_TOKEN>\nContent-Type: application/json\n\n{\n  "merchantOrderId": "PED-1048",\n  "amount": 15990,\n  "expiresAt": "2027-12-31T23:59:59Z"\n}`}</pre>
            <button
              onClick={() =>
                download(
                  "inpay-link-example.json",
                  JSON.stringify(
                    {
                      merchantOrderId: "PED-1048",
                      amount: 15990,
                      expiresAt: "2027-12-31T23:59:59Z",
                    },
                    null,
                    2,
                  ),
                  "application/json",
                )
              }
            >
              Baixar exemplo
            </button>
          </section>
          <section className="card">
            <h2>Referências da integração</h2>
            <a
              className="resource"
              target="_blank"
              rel="noreferrer"
              href="https://github.com/wrvevo/inpay/blob/main/docs/CONNECTORS.md"
            >
              Contratos dos connectors <ExternalLink size={16} />
            </a>
            <a
              className="resource"
              target="_blank"
              rel="noreferrer"
              href="https://github.com/wrvevo/inpay/blob/main/README.md"
            >
              Serviços e endereços Swagger <ExternalLink size={16} />
            </a>
            <Notice>
              Use tokens de adquirente válidos. O token-service local não
              fornece tokenização de produção. Nunca envie PAN ou CVV para o
              painel.
            </Notice>
            <h3>Idempotência</h3>
            <p>
              Reutilize operationId apenas em reenvios do mesmo comando.
              Resultados UNKNOWN exigem consulta antes de uma nova tentativa.
            </p>
          </section>
        </div>
      ) : (
        <section className="card">
          <Bell className="accent" />
          <h2>{tab}</h2>
          <Notice>
            {tab === "Webhooks"
              ? "Cadastro de destinos, validação de callbacks, assinatura e reentrega ainda precisam de endpoints administrativos. Nenhum webhook é enviado por esta interface."
              : "O Payment Core possui outbox e a projeção consome PaymentCreated. Histórico administrativo, busca de eventos e reprocessamento ainda não possuem APIs."}
          </Notice>
          <h3>Contrato de integração a implementar</h3>
          <ul className="rule-list">
            <li>Filtragem por lojista e período</li>
            <li>Verificação de assinatura e proteção contra replay</li>
            <li>Histórico de tentativas, erros e correlação</li>
            <li>Reprocessamento autorizado e idempotente</li>
          </ul>
        </section>
      )}
    </>
  );
}
