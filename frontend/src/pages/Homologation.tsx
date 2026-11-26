import { useEffect, useState } from "react";
import {
  Building2,
  ChevronDown,
  CircleUser,
  ClipboardCheck,
  CreditCard,
  Landmark,
  LayoutDashboard,
  Menu,
  ShieldCheck,
  Store,
  Users,
} from "lucide-react";
import { Heading, Metric, Notice } from "../components/UI";
import {
  acquirerCatalog,
  activeAcquirers,
  createUser,
  decideCase,
  homologationCases,
  openAnotherStore,
  saveCase,
  setAcquirerActive,
  startHomologation,
  storesOf,
  submitCase,
  updateProfile,
  usersOf,
} from "../access.mjs";

const METHODS = [
  ["pix", "PIX"],
  ["credit_card", "Cartão de crédito"],
  ["debit_card", "Cartão de débito"],
  ["boleto", "Boleto"],
];
const ANTIFRAUD = [
  ["INPAY_RISK", "InPay Risk", "Motor nativo do gateway, medido por tokens do plano."],
  ["CLEARSALE", "ClearSale", "Bureau externo para a análise da jornada."],
  ["SERASA_EXPERIAN", "Serasa Experian", "Consulta cadastral usada pelo time de antifraude."],
];
const STATUS: Record<string, string> = {
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
  if (digits.length !== 14) return value || "";
  return `${digits.slice(0, 2)}.${digits.slice(2, 5)}.${digits.slice(5, 8)}/${digits.slice(8, 12)}-${digits.slice(12)}`;
}

function formatAddress(address: any) {
  if (!address || typeof address === "string") return address || "Endereço ainda não consultado";
  return [address.street, address.number, address.district, address.city, address.state, address.zip]
    .filter(Boolean)
    .join(", ");
}

function connectorName(id: string) {
  return acquirerCatalog().find((item) => item.id === id)?.name || "Não escolhido";
}

function mapCompany(body: any) {
  const situation = String(body.descricao_situacao_cadastral || "").toUpperCase();
  return {
    cnpj: String(body.cnpj || "").replace(/\D/g, ""),
    legalName: body.razao_social || "",
    tradeName: body.nome_fantasia || body.razao_social || "",
    situation,
    situationLabel: situation === "ATIVA" ? "ATIVO" : "INATIVO",
    openedOn: body.data_inicio_atividade || "",
    legalNature: body.natureza_juridica || "",
    size: body.porte || "",
    branch: body.descricao_identificador_matriz_filial || "",
    mainActivity: body.cnae_fiscal_descricao || "",
    email: body.email || "",
    phone: body.ddd_telefone_1 || "",
    address: {
      zip: body.cep || "",
      street: body.logradouro || "",
      number: body.numero || "",
      complement: body.complemento || "",
      district: body.bairro || "",
      city: body.municipio || "",
      state: body.uf || "",
    },
    partners: (body.qsa || []).slice(0, 8).map((person: any) => ({
      name: person.nome_socio,
      role: person.qualificacao_socio,
    })),
    source: "Consulta pública de CNPJ",
    consultedAt: new Date().toISOString(),
  };
}

async function consultCnpj(cnpj: string) {
  const digits = String(cnpj || "").replace(/\D/g, "");
  if (digits.length !== 14) throw new Error("Informe um CNPJ com 14 dígitos.");
  const response = await fetch(`https://brasilapi.com.br/api/cnpj/v1/${digits}`, {
    signal: AbortSignal.timeout(8000),
  });
  if (!response.ok) throw new Error("A consulta pública não encontrou este CNPJ.");
  return mapCompany(await response.json());
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
                updateProfile(user.id, String(form.get("name")), String(form.get("email")), String(form.get("document"))),
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
  onUser,
}: {
  user: any;
  onLogout: () => void;
  onUser: (user: any) => void;
}) {
  const canEdit = ["PDV", "ADMIN"].includes(user.role);
  const canDecide = ["BACKOFFICE", "ADMIN"].includes(user.role);
  const navigation = [
    { group: "LOJAS", items: [["lojas", "Lojas em homologação", LayoutDashboard]] },
    {
      group: "JORNADA DA LOJA",
      items: [
        ["cadastro", "Cadastro do CNPJ", Building2],
        ["antifraude", "Antifraude", ShieldCheck],
        ["meios", "Meios e adquirentes", CreditCard],
        ["revisao", "Enviar jornada", ClipboardCheck],
        ["demo", "Loja demo", Store],
      ],
    },
    ...(user.role === "ADMIN"
      ? [{ group: "CATÁLOGO", items: [["adquirentes", "Adquirentes do gateway", Landmark]] }]
      : []),
    {
      group: "ACESSO",
      items: [
        ...(user.role === "ADMIN" ? [["equipe", "Equipe", Users]] : []),
        ["perfil", "Meus dados", CircleUser],
      ],
    },
  ];
  const pages = navigation.flatMap((group) => group.items.map((item) => String(item[0])));
  const [page, setPage] = useState(() => (pages.includes(location.hash.slice(1)) ? location.hash.slice(1) : "lojas"));
  const [menu, setMenu] = useState(false);
  const [rows, setRows] = useState(() => homologationCases());
  const [storeId, setStoreId] = useState("");
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");
  const current = rows.find((item: any) => item.storeId === storeId) || null;

  function refresh(nextStore = storeId) {
    const list = homologationCases();
    setRows(list);
    if (nextStore) setStoreId(nextStore);
  }
  function open(next: string, tenant = storeId) {
    if (!pages.includes(next)) next = "lojas";
    location.hash = next;
    setPage(next);
    if (tenant) setStoreId(tenant);
    setMenu(false);
    setError("");
    setMessage("");
    window.scrollTo(0, 0);
  }
  useEffect(() => {
    const listener = () => {
      const next = location.hash.slice(1);
      setPage(pages.includes(next) ? next : "lojas");
      setMenu(false);
    };
    window.addEventListener("hashchange", listener);
    if (!pages.includes(location.hash.slice(1))) location.hash = "lojas";
    return () => window.removeEventListener("hashchange", listener);
  }, [pages.join("|")]);
  function run(work: () => void, note = "Alteração registrada nesta sessão.") {
    setError("");
    setMessage("");
    try {
      work();
      refresh();
      setMessage(note);
    } catch (failure) {
      setError((failure as Error).message);
    }
  }
  function save(patch: Record<string, unknown>, note?: string) {
    if (!current) return;
    run(() => saveCase({ ...current, ...patch }, user.role), note);
  }

  const title = String(navigation.flatMap((group) => group.items).find((item) => item[0] === page)?.[1] || "Homologação");
  const waiting = rows.filter((item: any) => item.journeySubmitted && item.status === "EM_HOMOLOGACAO").length;
  const approved = rows.filter((item: any) => item.status === "APROVADA").length;

  let content;
  if (page === "lojas") {
    content = (
      <StorePortfolio
        role={user.role}
        rows={rows}
        canEdit={canEdit}
        onOpen={(id, next) => open(next, id)}
        onStarted={(id) => {
          refresh(id);
          open("cadastro", id);
          setMessage("A loja já acessa o gateway em homologação. Siga o cadastro até os adquirentes.");
        }}
        onError={setError}
        onAnother={(sourceId, name) => {
          const created = openAnotherStore(sourceId, name, user.role);
          refresh(created.storeId);
          open("cadastro", created.storeId);
          setMessage("Nova loja do mesmo CNPJ aberta em homologação.");
        }}
      />
    );
  } else if (!current) {
    content = (
      <section className="card">
        <Heading title={title} subtitle="Escolha uma loja da carteira para continuar a integração." />
        <button className="primary" onClick={() => open("lojas")}>Ver lojas</button>
      </section>
    );
  } else if (page === "cadastro") {
    content = <CompanyScreen current={current} canEdit={canEdit} onSave={(patch) => save(patch, "Cadastro atualizado com a consulta.")} />;
  } else if (page === "antifraude") {
    content = <AntifraudScreen key={current.id} current={current} canEdit={canEdit} onSave={(antifraud) => save({ antifraud })} />;
  } else if (page === "meios") {
    content = (
      <RoutesScreen
        key={current.id}
        current={current}
        canEdit={canEdit}
        onSave={(payments, connectors, connectorSettings) => save({ payments, connectors, connectorSettings })}
      />
    );
  } else if (page === "revisao") {
    content = (
      <ReviewScreen
        current={current}
        canEdit={canEdit}
        canDecide={canDecide}
        onSubmit={() => run(() => submitCase(current.id, user.role), "Jornada enviada para aprovação.")}
        onDecide={(approve) =>
          run(
            () => decideCase(current.id, user.role, approve),
            approve ? "Homologação aprovada. A loja demo foi criada com esta configuração." : "Jornada recusada. A loja continua no gateway em homologação.",
          )
        }
      />
    );
  } else if (page === "demo") {
    content = <DemoScreen current={current} />;
  } else if (page === "adquirentes") {
    content = <AcquirerDesk />;
  } else if (page === "equipe") {
    content = <TeamDesk user={user} onChange={() => refresh()} />;
  } else {
    content = <Profile user={user} onUser={onUser} />;
  }

  const dock = [
    ["lojas", "Lojas", LayoutDashboard],
    ["cadastro", "Cadastro", Building2],
    ["meios", "Adquirentes", CreditCard],
    ["revisao", "Jornada", ClipboardCheck],
  ];

  return (
    <>
      <a className="skip-link" href="#main-content">Pular para conteúdo</a>
      {menu && <button className="nav-backdrop" aria-label="Fechar menu" onClick={() => setMenu(false)} />}
      <aside className={menu ? "sidebar open" : "sidebar"}>
        <a className="brand" href="#lojas" onClick={(event) => { event.preventDefault(); open("lojas"); }}>
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
                  <button key={String(id)} aria-current={page === id ? "page" : undefined} className={page === id ? "active" : ""} onClick={() => open(String(id))}>
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
            {rows.length > 0 && (
              <select aria-label="Loja em homologação" value={current?.storeId || ""} onChange={(event) => setStoreId(event.target.value)}>
                <option value="">Selecione a loja</option>
                {rows.map((item: any) => (
                  <option value={item.storeId} key={item.storeId}>{item.storeName}</option>
                ))}
              </select>
            )}
            <span className="environment"><i />{ROLE[user.role] || user.role}<ChevronDown size={14} /></span>
          </div>
        </header>
        <div className="mode-banner demo">
          <span>
            <i />
            {waiting} jornada(s) aguardando aprovação · {approved} loja(s) demo criada(s). O gateway da loja em curso permanece em homologação.
          </span>
        </div>
        <main id="main-content" tabIndex={-1}>
          {error && <p role="alert" className="error">{error}</p>}
          {message && !error && <p role="status" className="saved-note">{message}</p>}
          {content}
        </main>
        <footer>
          InPay <span>Integração e homologação de lojas.</span>
          <small>{storesOf(user).length} acesso(s) de loja nesta conta</small>
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
          <button onClick={() => setMenu(true)}><Menu size={18} />Mais</button>
        </nav>
      </div>
    </>
  );
}

function StorePortfolio({
  role,
  rows,
  canEdit,
  onOpen,
  onStarted,
  onError,
  onAnother,
}: {
  role: string;
  rows: any[];
  canEdit: boolean;
  onOpen: (id: string, page: string) => void;
  onStarted: (id: string) => void;
  onError: (message: string) => void;
  onAnother: (sourceId: string, name: string) => void;
}) {
  const [cnpj, setCnpj] = useState("");
  const [storeName, setStoreName] = useState("");
  const [preview, setPreview] = useState<any>(null);
  const [busy, setBusy] = useState(false);
  const [localError, setLocalError] = useState("");
  return (
    <>
      <Heading
        title="Lojas em homologação"
        subtitle="Cada loja inicia a integração com o gateway já em homologação. A jornada cobre o cadastro do CNPJ, o antifraude e o adquirente de cada meio."
      />
      <div className="metrics">
        <Metric label="Lojas" value={String(rows.length)} note="Na carteira desta conta" />
        <Metric label="No gateway" value={String(rows.filter((item) => item.gatewayMode === "HOMOLOGACAO").length)} note="Acesso em homologação" />
        <Metric label="Aguardando" value={String(rows.filter((item) => item.journeySubmitted && item.status === "EM_HOMOLOGACAO").length)} note="Jornada enviada" />
        <Metric label="Demos" value={String(rows.filter((item) => item.demo).length)} note="Criadas na aprovação" />
      </div>
      {canEdit && (
        <form
          className="card form"
          onSubmit={async (event) => {
            event.preventDefault();
            setLocalError("");
            setBusy(true);
            try {
              const company = await consultCnpj(cnpj);
              setPreview(company);
            } catch (failure) {
              setPreview(null);
              setLocalError((failure as Error).message);
            } finally {
              setBusy(false);
            }
          }}
        >
          <h2>Iniciar homologação</h2>
          <div className="form-grid">
            <label>
              CNPJ
              <input value={cnpj} onChange={(event) => setCnpj(event.target.value)} inputMode="numeric" required placeholder="00.000.000/0000-00" />
            </label>
            <label>
              Nome da loja
              <input value={storeName} onChange={(event) => setStoreName(event.target.value)} required placeholder="Como a loja aparece no gateway" />
            </label>
          </div>
          <button className="primary" disabled={busy}>{busy ? "Consultando CNPJ" : "Consultar CNPJ"}</button>
          {localError && <p role="alert" className="error">{localError}</p>}
          {preview && (
            <div>
              <p>
                {preview.legalName} · situação <strong className={preview.situationLabel === "ATIVO" ? "situation on" : "situation off"}>{preview.situationLabel}</strong> ({preview.situation})
              </p>
              <p>{formatAddress(preview.address)}</p>
              <button
                type="button"
                className="primary"
                onClick={() => {
                  try {
                    const created = startHomologation(role, { ...preview, storeName });
                    onStarted(created.storeId);
                  } catch (failure) {
                    onError((failure as Error).message);
                  }
                }}
              >
                Abrir loja no gateway
              </button>
            </div>
          )}
        </form>
      )}
      <div className="store-board">
        {rows.map((item) => (
          <article className="card store-card" key={item.id}>
            <div className="store-card-head">
              <div>
                <h2>{item.storeName}</h2>
                <p>{formatCnpj(item.cnpj)} · {item.tradeName}</p>
              </div>
              <span className={`badge ${item.status === "APROVADA" ? "good" : item.status === "RECUSADA" ? "bad" : "neutral"}`}>
                <i />{STATUS[item.status] || item.status}
              </span>
            </div>
            <p>Gateway em homologação · {item.situationLabel || "situação não consultada"} · {item.journeySubmitted ? "jornada enviada" : "jornada em preenchimento"}</p>
            <div className="actions">
              <button className="primary" onClick={() => onOpen(item.storeId, item.demo ? "demo" : "cadastro")}>
                {item.demo ? "Abrir loja demo" : "Continuar integração"}
              </button>
            </div>
            {canEdit && <AnotherStore onCreate={(name) => onAnother(item.id, name)} />}
            {item.demo && <p>Demo criada: {item.demo.name}</p>}
          </article>
        ))}
      </div>
    </>
  );
}

function AnotherStore({ onCreate }: { onCreate: (name: string) => void }) {
  const [name, setName] = useState("");
  return (
    <form
      className="form"
      onSubmit={(event) => {
        event.preventDefault();
        onCreate(name);
        setName("");
      }}
    >
      <label>
        Nova loja deste CNPJ
        <input value={name} onChange={(event) => setName(event.target.value)} placeholder="Nome da outra loja" />
      </label>
      <button disabled={!name.trim()}>Abrir outra loja em homologação</button>
    </form>
  );
}

function CompanyScreen({ current, canEdit, onSave }: { current: any; canEdit: boolean; onSave: (patch: any) => void }) {
  const [busy, setBusy] = useState(false);
  const [note, setNote] = useState("");
  const address = current.address || {};
  return (
    <>
      <Heading title="Cadastro do CNPJ" subtitle={`${current.storeName} · dados usados pela integração e pelo time de antifraude.`} />
      <section className="card">
        <div className="store-card-head">
          <p>Origem: {current.source || "Consulta"} · {current.consultedAt ? new Date(current.consultedAt).toLocaleString("pt-BR") : "sem data"}</p>
          <strong className={current.situationLabel === "ATIVO" ? "situation on" : "situation off"}>{current.situationLabel} · {current.situation}</strong>
        </div>
        <dl className="dossier">
          <div><dt>Razão social</dt><dd>{current.legalName}</dd></div>
          <div><dt>Nome fantasia</dt><dd>{current.tradeName || "Não informado"}</dd></div>
          <div><dt>CNPJ</dt><dd>{formatCnpj(current.cnpj)}</dd></div>
          <div><dt>Abertura</dt><dd>{current.openedOn || "Não informada"}</dd></div>
          <div><dt>Natureza jurídica</dt><dd>{current.legalNature || "Não informada"}</dd></div>
          <div><dt>Porte</dt><dd>{current.size || "Não informado"} · {current.branch || ""}</dd></div>
          <div><dt>Atividade principal</dt><dd>{current.mainActivity || "Não informada"}</dd></div>
          <div><dt>Contato</dt><dd>{current.email || "sem e-mail"} · {current.phone || "sem telefone"}</dd></div>
          <div><dt>Endereço</dt><dd>{formatAddress(address)}{address.complement ? ` · ${address.complement}` : ""}</dd></div>
        </dl>
        <h2>Quadro para o antifraude</h2>
        {current.partners?.length ? (
          <ul className="people">
            {current.partners.map((person: any) => (
              <li key={person.name}><strong>{person.name}</strong><span>{person.role}</span></li>
            ))}
          </ul>
        ) : (
          <p>A consulta não trouxe sócios.</p>
        )}
        {canEdit && (
          <button
            className="primary"
            disabled={busy}
            onClick={async () => {
              setBusy(true);
              setNote("");
              try {
                onSave(await consultCnpj(current.cnpj));
              } catch (failure) {
                setNote((failure as Error).message);
              } finally {
                setBusy(false);
              }
            }}
          >
            {busy ? "Consultando" : "Atualizar consulta do CNPJ"}
          </button>
        )}
        {note && <p role="alert" className="error">{note}</p>}
        <Notice>A situação ATIVO ou INATIVO vem da consulta pública. O conector Serasa Experian continua sem chamada real.</Notice>
      </section>
    </>
  );
}

function AntifraudScreen({ current, canEdit, onSave }: { current: any; canEdit: boolean; onSave: (mode: string) => void }) {
  const [mode, setMode] = useState(current.antifraud || "INPAY_RISK");
  return (
    <>
      <Heading title="Antifraude" subtitle="Modo que a loja usará enquanto o gateway estiver em homologação." />
      <section className="card">
        <div className="choice-grid">
          {ANTIFRAUD.map(([id, label, note]) => (
            <button type="button" key={id} className={mode === id ? "choice on" : "choice"} aria-pressed={mode === id} onClick={() => canEdit && setMode(id)}>
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

function RoutesScreen({
  current,
  canEdit,
  onSave,
}: {
  current: any;
  canEdit: boolean;
  onSave: (payments: string[], connectors: Record<string, string>, settings: Record<string, Record<string, string>>) => void;
}) {
  const catalog = acquirerCatalog();
  const [selected, setSelected] = useState<string[]>(current.payments || []);
  const [connectors, setConnectors] = useState<Record<string, string>>(current.connectors || {});
  const [settings, setSettings] = useState<Record<string, Record<string, string>>>(current.connectorSettings || {});
  function toggle(id: string) {
    if (!canEdit) return;
    setSelected((items) => (items.includes(id) ? items.filter((item) => item !== id) : [...items, id]));
  }
  function choose(method: string, id: string) {
    setConnectors({ ...connectors, [method]: id });
    const acquirer = catalog.find((item) => item.id === id);
    if (!acquirer || settings[id]) return;
    setSettings({
      ...settings,
      [id]: Object.fromEntries(acquirer.fields.map((field: any) => [field.key, field.secret ? "" : field.value || ""])),
    });
  }
  const used = [...new Set(selected.map((method) => connectors[method]).filter(Boolean))];
  return (
    <>
      <Heading title="Meios e adquirentes" subtitle="Só entram adquirentes ativos. Ao escolher um, preencha a configuração essencial da integração." />
      <section className="card">
        <div className="route-list">
          {METHODS.map(([id, label]) => {
            const options = activeAcquirers(id);
            return (
              <div className="route" key={id}>
                <button type="button" className={selected.includes(id) ? "choice on" : "choice"} aria-pressed={selected.includes(id)} onClick={() => toggle(id)}>
                  <strong>{label}</strong>
                  <small>{selected.includes(id) ? "Vai para o gateway em homologação" : "Fora desta loja"}</small>
                </button>
                <label>
                  Adquirente
                  <select
                    aria-label={`Adquirente de ${label}`}
                    disabled={!canEdit || !selected.includes(id)}
                    value={connectors[id] || ""}
                    onChange={(event) => choose(id, event.target.value)}
                  >
                    <option value="">Selecione o connector</option>
                    {options.map((item) => (
                      <option value={item.id} key={item.id}>{item.name}</option>
                    ))}
                  </select>
                </label>
              </div>
            );
          })}
        </div>
        {used.map((id) => {
          const acquirer = catalog.find((item) => item.id === id);
          if (!acquirer) return null;
          return (
            <fieldset key={id} disabled={!canEdit}>
              <h2>{acquirer.name}</h2>
              <div className="form-grid">
                {acquirer.fields.map((field: any) => (
                  <label key={field.key}>
                    {field.label}
                    <input
                      value={settings[id]?.[field.key] ?? ""}
                      placeholder={field.secret ? "Nome do segredo, não o valor" : ""}
                      onChange={(event) =>
                        setSettings({ ...settings, [id]: { ...(settings[id] || {}), [field.key]: event.target.value } })
                      }
                    />
                  </label>
                ))}
              </div>
            </fieldset>
          );
        })}
        {canEdit && (
          <button className="primary" onClick={() => onSave(selected, connectors, settings)}>
            Salvar meios e configurações
          </button>
        )}
      </section>
    </>
  );
}

function AcquirerDesk() {
  const [items, setItems] = useState(() => acquirerCatalog());
  return (
    <>
      <Heading
        title="Adquirentes do gateway"
        subtitle="O administrador liga os conectores que podem ser escolhidos na jornada. Os que ainda estão em integração ficam fora da lista até serem ativados."
      />
      {["INTEGRADO", "EM_INTEGRACAO"].map((integration) => (
        <section className="card" key={integration}>
          <h2>{integration === "INTEGRADO" ? "Integrados no gateway" : "Em processo de integração"}</h2>
          <ul className="people">
            {items
              .filter((item) => item.integration === integration)
              .map((item) => (
                <li key={item.id}>
                  <strong>{item.name}</strong>
                  <span>{item.active ? "Ativo na lista" : "Fora da lista"}</span>
                  <small>{item.methods.map((method: string) => METHODS.find((row) => row[0] === method)?.[1] || method).join(" · ")}</small>
                  <button
                    aria-pressed={item.active}
                    onClick={() => {
                      setAcquirerActive("ADMIN", item.id, !item.active);
                      setItems(acquirerCatalog());
                    }}
                  >
                    {item.active ? "Desativar" : "Ativar"}
                  </button>
                </li>
              ))}
          </ul>
        </section>
      ))}
    </>
  );
}

function ReviewScreen({
  current,
  canEdit,
  canDecide,
  onSubmit,
  onDecide,
}: {
  current: any;
  canEdit: boolean;
  canDecide: boolean;
  onSubmit: () => void;
  onDecide: (approve: boolean) => void;
}) {
  const missing = (current.payments || []).filter((method: string) => !current.connectors?.[method]);
  return (
    <>
      <Heading title="Enviar jornada" subtitle={`${current.storeName} · gateway em homologação · ${STATUS[current.status] || current.status}`} />
      <section className="card">
        <dl className="dossier">
          <div><dt>Empresa</dt><dd>{current.legalName}</dd></div>
          <div><dt>Situação</dt><dd>{current.situationLabel} · {current.situation}</dd></div>
          <div><dt>Endereço</dt><dd>{formatAddress(current.address)}</dd></div>
          <div><dt>Antifraude</dt><dd>{ANTIFRAUD.find((item) => item[0] === current.antifraud)?.[1] || current.antifraud}</dd></div>
        </dl>
        <ul className="people">
          {(current.payments || []).map((method: string) => (
            <li key={method}>
              <strong>{METHODS.find((item) => item[0] === method)?.[1] || method}</strong>
              <span>{connectorName(current.connectors?.[method])}</span>
            </li>
          ))}
        </ul>
        {missing.length > 0 && <Notice>Falta o adquirente de {missing.join(", ")}.</Notice>}
        {!current.payments?.length && <Notice>Nenhum meio de pagamento foi escolhido.</Notice>}
        <div className="actions">
          {canEdit && current.status !== "APROVADA" && (
            <button className="primary" onClick={onSubmit}>Enviar jornada para aprovação</button>
          )}
          {canDecide && current.journeySubmitted && current.status === "EM_HOMOLOGACAO" && (
            <>
              <button className="primary" onClick={() => onDecide(true)}>Aprovar e criar loja demo</button>
              <button onClick={() => onDecide(false)}>Recusar jornada</button>
            </>
          )}
        </div>
        {current.journeySubmitted && current.status === "EM_HOMOLOGACAO" && !canDecide && (
          <p>A jornada está com o backoffice. A loja continua no gateway em homologação.</p>
        )}
      </section>
    </>
  );
}

function DemoScreen({ current }: { current: any }) {
  if (!current.demo) {
    return (
      <>
        <Heading title="Loja demo" subtitle="Ela é criada automaticamente quando a jornada é aprovada." />
        <Notice>
          {current.storeName} já acessa o gateway em homologação. A loja demo recebe a cópia do cadastro, do antifraude e dos adquirentes informados, para simular a integração.
        </Notice>
      </>
    );
  }
  return (
    <>
      <Heading title={current.demo.name} subtitle="Simulação criada com a configuração aprovada desta loja." />
      <section className="card">
        <dl className="dossier">
          <div><dt>Identificador</dt><dd>{current.demo.id}</dd></div>
          <div><dt>Empresa</dt><dd>{current.demo.company}</dd></div>
          <div><dt>CNPJ</dt><dd>{formatCnpj(current.demo.cnpj)}</dd></div>
          <div><dt>Antifraude</dt><dd>{ANTIFRAUD.find((item) => item[0] === current.demo.antifraud)?.[1]}</dd></div>
        </dl>
        <ul className="people">
          {current.demo.payments.map((method: string) => {
            const id = current.demo.connectors?.[method];
            const saved = current.demo.connectorSettings?.[id] || {};
            const visible = Object.entries(saved).filter(([key, value]) => key !== "secretRef" && value);
            return (
              <li key={method}>
                <strong>{METHODS.find((item) => item[0] === method)?.[1]}</strong>
                <span>{connectorName(id)}</span>
                <small>{visible.map(([key, value]) => `${key}: ${value}`).join(" · ")}</small>
              </li>
            );
          })}
        </ul>
        <Notice>Use esta loja para simular a integração. Nenhuma credencial de adquirente foi gravada aqui.</Notice>
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
          <label>Nome<input name="name" required /></label>
          <label>E-mail<input name="email" type="email" required /></label>
          <label>Documento<input name="document" required inputMode="numeric" /></label>
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
