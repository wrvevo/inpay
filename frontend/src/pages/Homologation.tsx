import { useEffect, useState } from "react";
import {
  Building2,
  ChevronDown,
  CircleUser,
  ClipboardCheck,
  Copy,
  CreditCard,
  LayoutDashboard,
  Menu,
  ShieldCheck,
  Store,
  Users,
  Wallet,
} from "lucide-react";
import { Heading, Metric, Notice } from "../components/UI";
import {
  cloneCase,
  createUser,
  decideCase,
  homologationCases,
  saveCase,
  storesOf,
  submitCase,
  updateProfile,
  usersOf,
} from "../access.mjs";

const PAYMENTS = [
  ["pix", "PIX", "Recebimento imediato na conta de liquidação."],
  ["credit_card", "Cartão de crédito", "À vista e parcelado, sujeito ao antifraude."],
  ["debit_card", "Cartão de débito", "Débito online no checkout da loja."],
  ["boleto", "Boleto", "Compensação bancária com vencimento."],
];
const ANTIFRAUD = [
  ["INPAY_RISK", "InPay Risk", "Motor nativo, com cobrança por tokens do plano."],
  ["CLEARSALE", "ClearSale", "Bureau externo. A chamada real permanece desligada."],
  ["SERASA_EXPERIAN", "Serasa Experian", "Consulta cadastral. A chamada real permanece desligada."],
];
const STATUS: Record<string, string> = {
  RASCUNHO: "Rascunho",
  EM_HOMOLOGACAO: "Em homologação",
  APROVADA: "Aprovada",
  RECUSADA: "Recusada",
};
const ROLE: Record<string, string> = {
  PDV: "Ponto de venda",
  BACKOFFICE: "Backoffice",
  ADMIN: "Administrador",
};

function formatCnpj(value: string) {
  const digits = String(value || "").replace(/\D/g, "");
  if (digits.length !== 14) return value;
  return `${digits.slice(0, 2)}.${digits.slice(2, 5)}.${digits.slice(5, 8)}/${digits.slice(8, 12)}-${digits.slice(12)}`;
}

function stepsOf(item: any) {
  return [
    { id: "contratante", label: "Contratante", done: Boolean(item?.legalName && item?.email && item?.phone && item?.address) },
    { id: "meios", label: "Meios", done: Boolean(item?.payments?.length) },
    { id: "antifraude", label: "Antifraude", done: Boolean(item?.antifraud) },
    { id: "liquidacao", label: "Liquidação", done: Boolean(item?.settlementAlias) },
    { id: "revisao", label: "Revisão", done: item?.status === "EM_HOMOLOGACAO" || item?.status === "APROVADA" },
    { id: "revisao", label: "Accounts", done: Boolean(item?.accountsPrepared) },
  ];
}

export function Profile({ user, onUser }: { user: any; onUser: (user: any) => void }) {
  const [message, setMessage] = useState("");
  return (
    <>
      <Heading title="Meus dados" subtitle="Nome, e-mail e documento usados no acesso desta conta." />
      <section className="card">
        <form
          className="form"
          onSubmit={(event) => {
            event.preventDefault();
            const form = new FormData(event.currentTarget);
            try {
              onUser(
                updateProfile(
                  user.id,
                  String(form.get("name")),
                  String(form.get("email")),
                  String(form.get("document")),
                ),
              );
              setMessage("Cadastro atualizado nesta sessão.");
            } catch (failure) {
              setMessage((failure as Error).message);
            }
          }}
        >
          <div className="form-grid">
            <label>
              Nome
              <input name="name" required defaultValue={user.name} autoComplete="name" />
            </label>
            <label>
              E-mail
              <input name="email" type="email" required defaultValue={user.email} autoComplete="email" />
            </label>
          </div>
          <label>
            Documento
            <input name="document" required defaultValue={user.document} inputMode="numeric" />
          </label>
          <button className="primary">Salvar cadastro</button>
          {message && <p role="status">{message}</p>}
        </form>
      </section>
    </>
  );
}

export function HomologationApp({
  user,
  onLogout,
}: {
  user: any;
  onLogout: () => void;
}) {
  const canEdit = ["PDV", "ADMIN"].includes(user.role);
  const canDecide = ["BACKOFFICE", "ADMIN"].includes(user.role);
  const navigation = [
    { group: "FILA", items: [["fila", "Visão da fila", LayoutDashboard]] },
    {
      group: "CADASTRO DA LOJA",
      items: [
        ["contratante", "Contratante", Building2],
        ["loja", "Dados da loja", Store],
        ["meios", "Meios de pagamento", CreditCard],
        ["antifraude", "Antifraude", ShieldCheck],
        ["liquidacao", "Liquidação", Wallet],
      ],
    },
    {
      group: "HOMOLOGAÇÃO",
      items: [
        ["revisao", "Revisão e decisão", ClipboardCheck],
        ...(canEdit ? [["clonar", "Clonar configuração", Copy]] : []),
      ],
    },
    {
      group: "ACESSO",
      items: [
        ...(user.role === "ADMIN" ? [["equipe", "Equipe", Users]] : []),
        ["perfil", "Meus dados", CircleUser],
      ],
    },
  ];
  const pages = navigation.flatMap((group) => group.items.map((item) => String(item[0])));
  const [page, setPage] = useState(() => (pages.includes(location.hash.slice(1)) ? location.hash.slice(1) : "fila"));
  const [menu, setMenu] = useState(false);
  const [rows, setRows] = useState(() => homologationCases());
  const [storeId, setStoreId] = useState(user.stores?.[0]?.id || "");
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");
  const visible = rows.filter((item: any) =>
    (user.stores || []).some((store: { id: string }) => store.id === item.storeId),
  );
  const current = visible.find((item: any) => item.storeId === storeId) || visible[0];
  const peers = visible.filter((item: any) => item.storeId !== current?.storeId && item.cnpj === current?.cnpj);

  function open(next: string, tenant = storeId) {
    if (!pages.includes(next)) next = "fila";
    location.hash = next;
    setPage(next);
    if (tenant) setStoreId(tenant);
    setMenu(false);
    setError("");
    window.scrollTo(0, 0);
  }
  useEffect(() => {
    const listener = () => {
      const next = location.hash.slice(1);
      setPage(pages.includes(next) ? next : "fila");
      setMenu(false);
    };
    window.addEventListener("hashchange", listener);
    if (!pages.includes(location.hash.slice(1))) location.hash = "fila";
    return () => window.removeEventListener("hashchange", listener);
  }, [pages.join("|")]);

  function run(work: () => void) {
    setError("");
    setMessage("");
    try {
      work();
      setRows(homologationCases());
      setMessage("Alteração registrada nesta sessão.");
    } catch (failure) {
      setError((failure as Error).message);
    }
  }
  function save(patch: Record<string, unknown>) {
    if (!current) return;
    run(() => saveCase({ ...current, ...patch }, user.role));
  }

  const title = String(navigation.flatMap((group) => group.items).find((item) => item[0] === page)?.[1] || "Homologação");
  const counts = {
    total: visible.length,
    waiting: visible.filter((item: any) => item.status === "EM_HOMOLOGACAO").length,
    approved: visible.filter((item: any) => item.status === "APROVADA").length,
    draft: visible.filter((item: any) => item.status === "RASCUNHO" || item.status === "RECUSADA").length,
  };

  let content;
  if (!current && page !== "perfil" && page !== "equipe") {
    content = <Notice>Nenhuma loja está vinculada a esta conta.</Notice>;
  } else if (page === "fila") {
    content = (
      <>
        <Heading
          title="Fila de homologação"
          subtitle="Cada loja segue o próprio cadastro. Lojas do mesmo CNPJ podem reaproveitar a configuração."
        />
        <div className="metrics">
          <Metric label="Lojas" value={String(counts.total)} note="No seu acesso" />
          <Metric label="Em análise" value={String(counts.waiting)} note="Aguardando backoffice" />
          <Metric label="Aprovadas" value={String(counts.approved)} note="Accounts preparado" />
          <Metric label="Para lançar" value={String(counts.draft)} note="Rascunho ou recusa" />
        </div>
        <div className="store-board">
          {visible.map((item: any) => (
            <article className="card store-card" key={item.id}>
              <div className="store-card-head">
                <div>
                  <h2>{item.storeName}</h2>
                  <p>{formatCnpj(item.cnpj)}</p>
                </div>
                <span className={`badge ${item.status === "APROVADA" ? "good" : item.status === "RECUSADA" ? "bad" : "neutral"}`}>
                  <i />
                  {STATUS[item.status] || item.status}
                </span>
              </div>
              <ol className="steps">
                {stepsOf(item).map((step) => (
                  <li key={step.label} className={step.done ? "done" : ""}>
                    {step.label}
                  </li>
                ))}
              </ol>
              <button className="primary" onClick={() => open(canEdit ? "contratante" : "revisao", item.storeId)}>
                {canEdit ? "Continuar cadastro" : "Revisar loja"}
              </button>
            </article>
          ))}
        </div>
      </>
    );
  } else if (page === "contratante") {
    content = (
      <>
        <Heading title="Contratante" subtitle={`${current.storeName} · dados da empresa responsável pela loja.`} />
        <form
          className="card form"
          key={current.id}
          onSubmit={(event) => {
            event.preventDefault();
            const form = new FormData(event.currentTarget);
            save({
              legalName: String(form.get("legalName")),
              email: String(form.get("email")),
              phone: String(form.get("phone")),
              address: String(form.get("address")),
            });
          }}
        >
          <fieldset disabled={!canEdit}>
            <div className="form-grid">
              <label>
                Razão social
                <input name="legalName" required defaultValue={current.legalName} />
              </label>
              <label>
                CNPJ do grupo
                <input value={formatCnpj(current.cnpj)} readOnly />
              </label>
              <label>
                E-mail comercial
                <input name="email" type="email" required defaultValue={current.email} />
              </label>
              <label>
                Telefone
                <input name="phone" required defaultValue={current.phone} inputMode="tel" />
              </label>
            </div>
            <label>
              Endereço
              <input name="address" required defaultValue={current.address} />
            </label>
          </fieldset>
          {canEdit && <button className="primary">Salvar contratante</button>}
        </form>
      </>
    );
  } else if (page === "loja") {
    content = (
      <>
        <Heading title="Dados da loja" subtitle="Identificação usada na homologação e no grupo do CNPJ." />
        <section className="card">
          <dl className="details">
            <div>
              <dt>Nome da loja</dt>
              <dd>{current.storeName}</dd>
            </div>
            <div>
              <dt>Identificador</dt>
              <dd>{current.storeId}</dd>
            </div>
            <div>
              <dt>CNPJ</dt>
              <dd>{formatCnpj(current.cnpj)}</dd>
            </div>
            <div>
              <dt>Situação</dt>
              <dd>{STATUS[current.status] || current.status}</dd>
            </div>
          </dl>
          <Notice>
            Outras lojas deste CNPJ: {peers.map((item: any) => item.storeName).join(", ") || "nenhuma nesta conta"}.
          </Notice>
          <button onClick={() => open("meios")}>Seguir para meios de pagamento</button>
        </section>
      </>
    );
  } else if (page === "meios") {
    content = (
      <PaymentScreen
        current={current}
        canEdit={canEdit}
        onSave={(payments) => save({ payments })}
      />
    );
  } else if (page === "antifraude") {
    content = (
      <AntifraudScreen
        current={current}
        canEdit={canEdit}
        onSave={(antifraud) => save({ antifraud })}
      />
    );
  } else if (page === "liquidacao") {
    content = (
      <>
        <Heading title="Liquidação" subtitle="Conta que o Accounts recebe quando a loja for aprovada." />
        <form
          className="card form"
          key={current.id + current.settlementAlias}
          onSubmit={(event) => {
            event.preventDefault();
            const form = new FormData(event.currentTarget);
            save({
              settlementAlias: String(form.get("settlementAlias")),
              splitEnabled: form.get("splitEnabled") === "on",
            });
          }}
        >
          <fieldset disabled={!canEdit}>
            <label>
              Apelido da conta no Accounts
              <input name="settlementAlias" required defaultValue={current.settlementAlias} />
            </label>
            <label className="check">
              <input name="splitEnabled" type="checkbox" defaultChecked={current.splitEnabled} />
              Esta loja reparte recebíveis entre recebedores
            </label>
          </fieldset>
          <Notice>
            A aprovação marca a pré-configuração. Nenhuma conta bancária é aberta nesta tela.
          </Notice>
          {canEdit && <button className="primary">Salvar liquidação</button>}
        </form>
      </>
    );
  } else if (page === "revisao") {
    content = (
      <>
        <Heading title="Revisão e decisão" subtitle={`${current.storeName} · ${STATUS[current.status] || current.status}`} />
        <section className="card">
          <dl className="details">
            <div><dt>Contratante</dt><dd>{current.legalName}</dd></div>
            <div><dt>Contato</dt><dd>{current.email} · {current.phone}</dd></div>
            <div><dt>Endereço</dt><dd>{current.address}</dd></div>
            <div><dt>Meios</dt><dd>{current.payments.map((item: string) => PAYMENTS.find((row) => row[0] === item)?.[1] || item).join(", ") || "Nenhum"}</dd></div>
            <div><dt>Antifraude</dt><dd>{ANTIFRAUD.find((item) => item[0] === current.antifraud)?.[1] || current.antifraud}</dd></div>
            <div><dt>Liquidação</dt><dd>{current.settlementAlias}{current.splitEnabled ? " · com split" : ""}</dd></div>
          </dl>
          <div className="actions">
            {canEdit && current.status !== "APROVADA" && (
              <button className="primary" onClick={() => run(() => submitCase(current.id, user.role))}>
                Enviar para homologação
              </button>
            )}
            {canDecide && current.status === "EM_HOMOLOGACAO" && (
              <>
                <button className="primary" onClick={() => run(() => decideCase(current.id, user.role, true))}>
                  Aprovar loja
                </button>
                <button onClick={() => run(() => decideCase(current.id, user.role, false))}>
                  Recusar e devolver
                </button>
              </>
            )}
          </div>
          {current.accountsPrepared && (
            <p role="status">Accounts marcado como preparado para o fluxo transacional desta loja.</p>
          )}
          {current.status === "RASCUNHO" && canDecide && !canEdit && (
            <Notice>O ponto de venda ainda não enviou esta loja.</Notice>
          )}
        </section>
      </>
    );
  } else if (page === "clonar") {
    content = (
      <>
        <Heading title="Clonar configuração" subtitle="Copia meios, antifraude e liquidação para outra loja do mesmo CNPJ." />
        <form
          className="card form"
          onSubmit={(event) => {
            event.preventDefault();
            const form = new FormData(event.currentTarget);
            run(() => cloneCase(current.id, String(form.get("target")), user.role));
          }}
        >
          <p>
            Origem: <strong>{current.storeName}</strong> · {formatCnpj(current.cnpj)}
          </p>
          <label>
            Loja de destino
            <select name="target" required defaultValue={peers[0]?.storeId || ""}>
              {peers.map((item: any) => (
                <option value={item.storeId} key={item.storeId}>
                  {item.storeName} · {STATUS[item.status] || item.status}
                </option>
              ))}
            </select>
          </label>
          {peers.length === 0 && <Notice>Não há outra loja deste CNPJ na sua conta.</Notice>}
          <button className="primary" disabled={peers.length === 0}>Clonar e deixar como rascunho</button>
        </form>
      </>
    );
  } else if (page === "equipe") {
    content = <TeamDesk user={user} onChange={() => setRows(homologationCases())} />;
  } else {
    content = <Profile user={user} onUser={() => setMessage("Cadastro atualizado nesta sessão.")} />;
  }

  const dock = [
    ["fila", "Fila", LayoutDashboard],
    ["loja", "Loja", Store],
    ["meios", "Meios", CreditCard],
    ["revisao", "Revisão", ClipboardCheck],
  ];

  return (
    <>
      <a className="skip-link" href="#main-content">Pular para conteúdo</a>
      {menu && <button className="nav-backdrop" aria-label="Fechar menu" onClick={() => setMenu(false)} />}
      <aside className={menu ? "sidebar open" : "sidebar"}>
        <a className="brand" href="#fila" onClick={(event) => { event.preventDefault(); open("fila"); }}>
          <span className="brand-symbol">in</span>InPay<span className="brand-dot">.</span>
        </a>
        <div className="workspace">
          <span className="workspace-logo">HM</span>
          <span>
            <strong>Homologação</strong>
            <small>{ROLE[user.role] || user.role}</small>
          </span>
        </div>
        <nav aria-label="Menu de homologação">
          {navigation.map((group) => (
            <div className="nav-group" key={group.group}>
              <span>{group.group}</span>
              {group.items.map(([id, label, Icon]) => {
                const Glyph = Icon as typeof Store;
                return (
                  <button
                    key={String(id)}
                    aria-current={page === id ? "page" : undefined}
                    className={page === id ? "active" : ""}
                    onClick={() => open(String(id))}
                  >
                    <Glyph size={18} />
                    <span>{String(label)}</span>
                  </button>
                );
              })}
            </div>
          ))}
        </nav>
        <div className="sidebar-bottom">
          <button className="profile" onClick={() => open("perfil")}>
            <span>{String(user.name || "HM").slice(0, 2).toUpperCase()}</span>
            <div>
              <strong>{user.name}</strong>
              <small>{user.email}</small>
            </div>
          </button>
          <button onClick={onLogout}>Sair</button>
        </div>
      </aside>
      <div className="shell homolog">
        <header className="topbar">
          <div className="breadcrumbs">
            <button className="icon-button mobile-menu" aria-label="Abrir menu" onClick={() => setMenu(true)}>
              <Menu />
            </button>
            <span>Homologação</span>
            <span>/</span>
            <strong>{title}</strong>
          </div>
          <div className="topbar-actions">
            {visible.length > 0 && (
              <select
                aria-label="Loja em homologação"
                value={current?.storeId || ""}
                onChange={(event) => setStoreId(event.target.value)}
              >
                {visible.map((item: any) => (
                  <option value={item.storeId} key={item.storeId}>
                    {item.storeName}
                  </option>
                ))}
              </select>
            )}
            <span className="environment">
              <i />
              {ROLE[user.role] || user.role}
              <ChevronDown size={14} />
            </span>
          </div>
        </header>
        <div className="mode-banner demo">
          <span>
            <i />
            {user.role === "BACKOFFICE"
              ? "Valide o cadastro e decida as lojas que já estão em homologação."
              : user.role === "ADMIN"
                ? "Você lança, aprova e cria PDV, backoffice e outros administradores."
                : "Lance os dados da loja e envie. A decisão fica com o backoffice."}
          </span>
        </div>
        <main id="main-content" tabIndex={-1}>
          {error && <p role="alert" className="error">{error}</p>}
          {message && !error && <p role="status" className="saved-note">{message}</p>}
          {content}
        </main>
        <footer>
          InPay <span>Homologação de lojas.</span>
          <small>{storesOf(user).length} loja(s) nesta conta</small>
        </footer>
        <nav className="homolog-dock" aria-label="Atalhos">
          {dock.map(([id, label, Icon]) => {
            const Glyph = Icon as typeof Store;
            return (
              <button key={String(id)} className={page === id ? "active" : ""} onClick={() => open(String(id))}>
                <Glyph size={18} />
                {String(label)}
              </button>
            );
          })}
          <button onClick={() => setMenu(true)}>
            <Menu size={18} />
            Mais
          </button>
        </nav>
      </div>
    </>
  );
}

function PaymentScreen({
  current,
  canEdit,
  onSave,
}: {
  current: any;
  canEdit: boolean;
  onSave: (payments: string[]) => void;
}) {
  const [selected, setSelected] = useState<string[]>(current.payments);
  useEffect(() => setSelected(current.payments), [current.id, current.payments.join("|")]);
  function toggle(id: string) {
    if (!canEdit) return;
    setSelected((items) => (items.includes(id) ? items.filter((item) => item !== id) : [...items, id]));
  }
  return (
    <>
      <Heading title="Meios de pagamento" subtitle="Escolha o que a loja poderá oferecer no checkout." />
      <section className="card">
        <div className="choice-grid">
          {PAYMENTS.map(([id, label, note]) => (
            <button
              type="button"
              key={id}
              className={selected.includes(id) ? "choice on" : "choice"}
              aria-pressed={selected.includes(id)}
              onClick={() => toggle(id)}
            >
              <strong>{label}</strong>
              <small>{note}</small>
            </button>
          ))}
        </div>
        {canEdit && (
          <button className="primary" onClick={() => onSave(selected)}>
            Salvar meios
          </button>
        )}
      </section>
    </>
  );
}

function AntifraudScreen({
  current,
  canEdit,
  onSave,
}: {
  current: any;
  canEdit: boolean;
  onSave: (mode: string) => void;
}) {
  const [mode, setMode] = useState(current.antifraud);
  useEffect(() => setMode(current.antifraud), [current.id, current.antifraud]);
  return (
    <>
      <Heading title="Antifraude" subtitle="Modo que será pré-configurado para esta loja." />
      <section className="card">
        <div className="choice-grid">
          {ANTIFRAUD.map(([id, label, note]) => (
            <button
              type="button"
              key={id}
              className={mode === id ? "choice on" : "choice"}
              aria-pressed={mode === id}
              onClick={() => canEdit && setMode(id)}
            >
              <strong>{label}</strong>
              <small>{note}</small>
            </button>
          ))}
        </div>
        {canEdit && <button className="primary" onClick={() => onSave(mode)}>Salvar antifraude</button>}
      </section>
    </>
  );
}

function TeamDesk({ user, onChange }: { user: any; onChange: () => void }) {
  const [people, setPeople] = useState(() => usersOf(user));
  const [error, setError] = useState("");
  return (
    <>
      <Heading title="Equipe de homologação" subtitle="O administrador cria PDV, backoffice e outros administradores." />
      <section className="card">
        <ul className="people">
          {people.map((person: any) => (
            <li key={person.id}>
              <strong>{person.name}</strong>
              <span>{ROLE[person.role] || person.role}</span>
              <small>{person.email}</small>
            </li>
          ))}
        </ul>
      </section>
      <form
        className="card form"
        onSubmit={(event) => {
          event.preventDefault();
          const form = new FormData(event.currentTarget);
          setError("");
          try {
            createUser(user, {
              role: String(form.get("role")),
              name: String(form.get("name")),
              email: String(form.get("email")),
              document: String(form.get("document")),
              menus: [],
              stores: user.stores.map((store: { id: string }) => store.id),
            });
            setPeople(usersOf(user));
            onChange();
            event.currentTarget.reset();
          } catch (failure) {
            setError((failure as Error).message);
          }
        }}
      >
        <h2>Novo acesso</h2>
        <div className="form-grid">
          <label>
            Nome
            <input name="name" required />
          </label>
          <label>
            E-mail
            <input name="email" type="email" required />
          </label>
          <label>
            Documento
            <input name="document" required inputMode="numeric" />
          </label>
          <label>
            Perfil
            <select name="role" defaultValue="PDV">
              <option value="PDV">Ponto de venda</option>
              <option value="BACKOFFICE">Backoffice</option>
              <option value="ADMIN">Administrador</option>
            </select>
          </label>
        </div>
        {error && <p role="alert" className="error">{error}</p>}
        <button className="primary">Criar acesso</button>
      </form>
    </>
  );
}
