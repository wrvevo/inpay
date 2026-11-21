import { useEffect, useState } from "react";
import {
  ShoppingBag,
  Activity,
  ArrowUpRight,
  Building2,
  ChevronDown,
  Code2,
  CreditCard,
  GitBranch,
  LayoutDashboard,
  Link2,
  Menu,
  Settings as SettingsIcon,
  ShieldCheck,
  Split as SplitIcon,
  Users,
  Wallet,
  Repeat,
  CircleHelp,
  Search,
  X,
  PanelLeftClose,
} from "lucide-react";
import ProductModule from "./pages/ProductModule";
import Billing from "./pages/Billing";
import Marketplace from "./pages/Marketplace";
import Overview from "./pages/Overview";
import Transactions from "./pages/Transactions";
import Links from "./pages/Links";
import Methods from "./pages/Methods";
import {
  Directory,
  Finance,
  Fraud,
  Journey,
  Routing,
  Split,
} from "./pages/Operations";
import { Developers, Settings } from "./pages/Settings";
import { Modal } from "./components/UI";
import type { Session } from "./api";
const navigation = [
  { group: "TIME INPAY", items: [["billing", "Gestão financeira", Wallet]] },
  {
    group: "INPAY ACCOUNTS",
    items: [["accounts", "Contas financeiras", Building2]],
  },
  {
    group: "INPAY MARKETPLACE",
    items: [["commerce", "Vendedores e repasses", ShoppingBag]],
  },
  {
    group: "INPAY CREDITS",
    items: [
      ["credits", "Créditos e benefícios", Wallet],
      ["my-credit", "Meu crédito e compras", ShoppingBag],
    ],
  },
  {
    group: "VISÃO DO NEGÓCIO",
    items: [
      ["overview", "Visão geral", LayoutDashboard],
      ["journey", "Jornada do pagamento", GitBranch],
    ],
  },
  {
    group: "INPAY GATEWAY",
    items: [
      ["transactions", "Transações", CreditCard],
      ["links", "Links de pagamento", Link2],
      ["subscriptions", "Assinaturas", Repeat],
      ["customers", "Clientes", Users],
      ["recipients", "Recebedores", Building2],
      ["split", "Split de pagamentos", SplitIcon],
    ],
  },
  {
    group: "GESTÃO E CONTROLE",
    items: [
      ["finance", "Financeiro", Wallet],
      ["fraud", "Antifraude", ShieldCheck],
      ["disputes", "Contestações", Activity],
      ["methods", "Meios de pagamento", CreditCard],
      ["routing", "Roteamento", GitBranch],
      ["developers", "Desenvolvedores", Code2],
      ["team", "Equipe e permissões", Users],
      ["settings", "Configurações", SettingsIcon],
    ],
  },
];
const validPages = navigation.flatMap((g) => g.items.map((i) => String(i[0])));
function currentPage() {
  const page = location.hash.slice(1);
  return validPages.includes(page) ? page : "overview";
}

/** The mode/tenant key destroys page state on context changes to prevent data mixing. */
export default function App() {
  const [page, setPage] = useState(currentPage);
  const [menu, setMenu] = useState(false);
  const [help, setHelp] = useState(false);
  const [search, setSearch] = useState("");
  const [session, setSession] = useState<Session>({
    mode: "demo",
    token: "",
    tenant: "11111111-1111-1111-1111-111111111111",
  });
  useEffect(() => {
    const listener = () => {
      setPage(currentPage());
      setMenu(false);
    };
    window.addEventListener("hashchange", listener);
    return () => window.removeEventListener("hashchange", listener);
  }, []);
  function navigate(next: string) {
    location.hash = next;
    setPage(next);
    setSearch("");
    setMenu(false);
    window.scrollTo(0, 0);
  }
  const visibleNavigation =
    page === "my-credit"
      ? [
          {
            group: "MINHA CONTA",
            items: [
              ["my-credit", "Meu crédito e compras", ShoppingBag],
              ["settings", "Acesso e configurações", SettingsIcon],
            ],
          },
        ]
      : navigation;
  const activeName = navigation
    .flatMap((g) => g.items)
    .find((i) => i[0] === page)?.[1];
  let content;
  if (page === "billing") content = <Billing session={session} />;
  else if (page === "accounts")
    content = <ProductModule product="accounts" session={session} />;
  else if (page === "commerce")
    content = <ProductModule product="marketplace" session={session} />;
  else if (page === "credits") content = <Marketplace session={session} />;
  else if (page === "my-credit")
    content = <Marketplace session={session} customer />;
  else if (page === "overview")
    content = <Overview session={session} navigate={navigate} />;
  else if (page === "transactions")
    content = <Transactions session={session} />;
  else if (page === "links") content = <Links session={session} />;
  else if (page === "methods") content = <Methods />;
  else if (page === "fraud") content = <Fraud session={session} />;
  else if (page === "routing") content = <Routing />;
  else if (page === "split") content = <Split />;
  else if (page === "journey") content = <Journey />;
  else if (page === "finance") content = <Finance session={session} />;
  else if (page === "settings")
    content = <Settings session={session} onSession={setSession} />;
  else if (page === "developers") content = <Developers />;
  else
    content = (
      <Directory
        kind={
          page as
            | "customers"
            | "recipients"
            | "subscriptions"
            | "disputes"
            | "team"
        }
        session={session}
      />
    );
  return (
    <>
      <a className="skip-link" href="#main-content">
        Pular para conteúdo
      </a>
      {menu && (
        <button
          className="nav-backdrop"
          aria-label="Fechar menu"
          onClick={() => setMenu(false)}
        />
      )}
      <aside className={menu ? "sidebar open" : "sidebar"}>
        <a className="brand" href="#overview" aria-label="InPay início">
          <span className="brand-symbol">in</span>InPay
          <span className="brand-dot">.</span>
        </a>
        <button className="workspace" onClick={() => navigate("settings")}>
          <span className="workspace-logo">IL</span>
          <span>
            <strong>Include Labs</strong>
            <small>
              {session.mode === "demo"
                ? "Workspace demonstrativo"
                : "Contexto conectado"}
            </small>
          </span>
          <ChevronDown size={15} />
        </button>
        <nav aria-label="Menu principal">
          {visibleNavigation.map((group) => (
            <div className="nav-group" key={group.group}>
              <span>{group.group}</span>
              {group.items.map(([id, label, Icon]) => {
                const Glyph = Icon as typeof Activity;
                return (
                  <button
                    aria-label={String(label)}
                    aria-current={page === id ? "page" : undefined}
                    key={String(id)}
                    className={page === id ? "active" : ""}
                    onClick={() => navigate(String(id))}
                  >
                    <Glyph size={18} />
                    <span>{String(label)}</span>
                    {id === "links" && <small>NOVO</small>}
                  </button>
                );
              })}
            </div>
          ))}
        </nav>
        <div className="sidebar-bottom">
          <div className="support">
            <CircleHelp size={18} />
            <div>
              <strong>Precisa de uma mão?</strong>
              <button onClick={() => setHelp(true)}>
                Guia do painel <ArrowUpRight size={14} />
              </button>
            </div>
          </div>
          <button className="profile" onClick={() => navigate("settings")}>
            <span>IL</span>
            <div>
              <strong>
                {session.mode === "demo"
                  ? page === "my-credit"
                    ? "Cliente demo"
                    : "Administrador demo"
                  : "Sessão técnica"}
              </strong>
              <small>Include Labs · InPay</small>
            </div>
            <PanelLeftClose size={16} />
          </button>
        </div>
      </aside>
      <div className="shell">
        <header className="topbar">
          <div className="breadcrumbs">
            <button
              className="icon-button mobile-menu"
              aria-label="Abrir menu"
              onClick={() => setMenu(true)}
            >
              <Menu />
            </button>
            <span>Painel gerencial</span>
            <span>/</span>
            <strong>{String(activeName)}</strong>
          </div>
          <div className="topbar-actions">
            <div className="global-search">
              <Search size={16} />
              <input
                aria-label="Buscar páginas"
                placeholder="Buscar no painel"
                value={search}
                onChange={(e) => setSearch(e.target.value)}
              />
              {search && (
                <div className="search-results">
                  {visibleNavigation
                    .flatMap((g) => g.items)
                    .filter((i) =>
                      String(i[1]).toLowerCase().includes(search.toLowerCase()),
                    )
                    .map((i) => (
                      <button
                        key={String(i[0])}
                        onClick={() => navigate(String(i[0]))}
                      >
                        {String(i[1])}
                      </button>
                    ))}
                </div>
              )}
            </div>
            <button
              className="environment"
              onClick={() => navigate("settings")}
            >
              <i />
              {session.mode === "demo" ? "Demonstração" : "Conectado"}
              <ChevronDown size={14} />
            </button>
            <button
              className="icon-button"
              aria-label="Ajuda"
              onClick={() => setHelp(true)}
            >
              <CircleHelp size={19} />
            </button>
          </div>
        </header>
        <div className={`mode-banner ${session.mode}`}>
          <span>
            <i />
            {session.mode === "demo"
              ? "Modo demonstração · dados fictícios, nenhuma movimentação financeira."
              : "Modo conectado · somente dados retornados pelas APIs; áreas pendentes não exibem métricas simuladas."}
          </span>
          <button onClick={() => navigate("settings")}>
            {session.mode === "demo" ? "Conectar APIs" : "Gerenciar sessão"}{" "}
            <ArrowUpRight size={14} />
          </button>
        </div>
        <main
          id="main-content"
          key={`${page}:${session.mode}:${session.tenant}:${session.token}`}
          tabIndex={-1}
        >
          {content}
        </main>
        <footer>
          InPay <span>Infraestrutura para conectar negócios.</span>
          <small>Include Labs · Painel 0.1.0</small>
        </footer>
      </div>
      {help && (
        <Modal title="Bem-vindo ao painel InPay" onClose={() => setHelp(false)}>
          <p>
            Explore a demonstração para conhecer a jornada, filtrar transações,
            simular split e antifraude e configurar templates dos provedores.
          </p>
          <p>
            Em Configurações, selecione o modo conectado para consultar
            transações e criar, consultar ou revogar registros de links. Os
            serviços validam o escopo e o lojista em cada chamada.
          </p>
          <p>
            Cadastros de clientes, recebedores, assinaturas e equipe são
            demonstrações em memória. Templates de adquirentes são exportados e
            não alteram o servidor.
          </p>
          <button
            className="primary"
            onClick={() => {
              setHelp(false);
              navigate("journey");
            }}
          >
            Conhecer a jornada
          </button>
        </Modal>
      )}
    </>
  );
}
