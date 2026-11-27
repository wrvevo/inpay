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
import { Login } from "./pages/Login";
import { RiskDesk, ProviderSetup } from "./pages/GatewayAdmin";
import { TeamAccess } from "./pages/TeamAccess";
import ConnectedHomologation from "./pages/ConnectedHomologation";
import { HomologationApp, Profile } from "./pages/Homologation";
import { Modal } from "./components/UI";
import type { Session } from "./api";
// Product menus share the existing shell and page IDs, not responsibilities.
const products = [{ id: "gateway", label: "InPay Gateway", groups: [{ group: "OPERAÇÃO DE PAGAMENTOS", items: [["overview", "Visão geral", LayoutDashboard],
["transactions", "Transações", CreditCard],
["links", "Links de pagamento", Link2],
["subscriptions", "Assinaturas", Repeat],
["disputes", "Contestações", Activity]] },
{ group: "RISCO E INTEGRAÇÃO", items: [["fraud", "Antifraude", ShieldCheck],
["risk", "Gestão InRisk", ShieldCheck],
["clearsale", "ClearSale", ShieldCheck],
["serasa", "Serasa Experian", ShieldCheck],
["routing", "Roteamento", GitBranch],
["developers", "Desenvolvedores", Code2],
["journey", "Jornada do pagamento", GitBranch]] }] },
{ id: "accounts", label: "InPay Accounts", groups: [{ group: "CONTAS E CONFIGURAÇÃO", items: [["accounts", "Contas financeiras e bancos", Building2],
["methods", "Meios e adquirentes (templates)", CreditCard],
["customers", "Clientes", Users],
["finance", "Financeiro", Wallet]] }] },
{ id: "marketplace", label: "InPay Marketplace", groups: [{ group: "GESTÃO COMERCIAL", items: [["commerce", "Vendedores e repasses", ShoppingBag],
["recipients", "Recebedores", Building2],
["split", "Split de pagamentos", SplitIcon]] }] },
{ id: "credits", label: "InPay Credits", groups: [{ group: "BENEFÍCIOS", items: [["credits", "Créditos e benefícios", Wallet],
["my-credit", "Meu crédito e compras", ShoppingBag]] }] },
{ id: "billing", label: "InPay Billing · interno", groups: [{ group: "TIME INPAY", items: [["billing", "Gestão financeira", Wallet]] }] }];
const sharedNavigation = [{ group: "ACESSO", items: [["team", "Equipe e permissões", Users],
["profile", "Meus dados", Users],
["settings", "Configurações", SettingsIcon]] }];
const navigation = [...products.flatMap((product) => product.groups), ...sharedNavigation];
function productFor(page: string) {
  return products.find((product) => product.groups.some((group) => group.items.some(([id]) => id === page)));
}
const validPages = navigation.flatMap((g) => g.items.map((i) => String(i[0])));
function normalizeSearch(value: string) {
  return value.normalize("NFD").replace(/\p{Diacritic}/gu, "").toLowerCase().trim();
}
const MENU_OF: Record<string, string> = {
  overview: "overview",
  transactions: "transactions",
  fraud: "fraud",
  risk: "risk",
  clearsale: "clearsale",
  serasa: "serasa",
  methods: "methods",
  billing: "billing",
  accounts: "accounts",
  team: "team",
  settings: "settings",
  profile: "settings",
};
function currentPage() {
  const page = location.hash.slice(1);
  return validPages.includes(page) ? page : "overview";
}

function readUser() {
  try {
    return JSON.parse(sessionStorage.getItem("inpay-user") || "null");
  } catch {
    return null;
  }
}
/** The mode/tenant key destroys page state on context changes to prevent data mixing. */
export default function App() {
  const [page, setPage] = useState(currentPage);
  const [workspace, setWorkspace] = useState(() => productFor(currentPage())?.id || "gateway");
  const [menu, setMenu] = useState(false);
  const [help, setHelp] = useState(false);
  const [search, setSearch] = useState("");
  const [user, setUser] = useState<any>(readUser);
  const [session, setSession] = useState<Session>({
    mode: "demo",
    token: "",
    tenant: readUser()?.stores?.[0]?.id || "11111111-1111-1111-1111-111111111111",
  });
  function enter(next: any, redirect = true) {
    sessionStorage.setItem("inpay-user", JSON.stringify(next));
    setUser(next);
    setSession((current) => ({
      ...current,
      tenant: next.stores?.[0]?.id || current.tenant,
    }));
    if (redirect) {
      location.hash = next.realm === "HOMOLOGATION" ? "homologation" : "overview";
      setPage("overview");
      setWorkspace("gateway");
    }
  }
  function logout() {
    sessionStorage.removeItem("inpay-user");
    setUser(null);
    setSession({ mode: "demo", token: "", tenant: "11111111-1111-1111-1111-111111111111" });
  }
  useEffect(() => {
    const listener = () => {
      const next = currentPage();
      setPage(next);
      const product = productFor(next);
      if (product) setWorkspace(product.id);
      setMenu(false);
    };
    window.addEventListener("hashchange", listener);
    return () => window.removeEventListener("hashchange", listener);
  }, []);
  function canAccess(id: string) {
    if (!user || user.realm !== "PANEL") return false;
    if (user.role === "ADMIN") return true;
    const key = MENU_OF[id];
    return Boolean(key && user.menus?.includes(key));
  }
  const availableProducts = products.filter((product) =>
    product.groups.some((group) => group.items.some(([id]) => canAccess(String(id)))),
  );
  const activeProduct = (canAccess(page) ? productFor(page) : undefined) || availableProducts.find((product) => product.id === workspace) || availableProducts[0];
  const visibleNavigation = [...(activeProduct?.groups || []), ...sharedNavigation]
    .map((group) => ({ ...group, items: group.items.filter(([id]) => canAccess(String(id))) }))
    .filter((group) => group.items.length);
  const searchQuery = normalizeSearch(search);
  const searchResults = navigation.flatMap((group) => group.items)
    .filter(([id, label]) => canAccess(String(id)) && normalizeSearch(String(label)).includes(searchQuery));
  function navigate(next: string) {
    if (!validPages.includes(next) || !canAccess(next)) return;
    const target = productFor(next);
    if (target) setWorkspace(target.id);
    location.hash = next;
    setPage(next);
    setSearch("");
    setMenu(false);
    window.scrollTo(0, 0);
  }
  function switchProduct(id: string) {
    const target = availableProducts.find((product) => product.id === id);
    const first = target?.groups.flatMap((group) => group.items).find(([pageId]) => canAccess(String(pageId)));
    if (first) navigate(String(first[0]));
  }
  const activeName = navigation
    .flatMap((g) => g.items)
    .find((i) => i[0] === page)?.[1];
  let content;
  if (!canAccess(page)) content = <section className="card"><h2>Acesso restrito</h2><p>Selecione uma área liberada para sua conta.</p></section>;
  else if (page === "billing") content = <Billing session={session} />;
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
  else if (page === "risk") content = <RiskDesk />;
  else if (page === "clearsale") content = <ProviderSetup provider="clearsale" />;
  else if (page === "serasa") content = <ProviderSetup provider="serasa" />;
  else if (page === "team") content = <TeamAccess user={user} onUser={(next) => enter(next, false)} />;
  else if (page === "profile") content = <Profile user={user} onUser={(next) => enter(next, false)} />;
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
        }
        session={session}
      />
    );
  if (!user) return <Login onLogin={enter} />;
  if (user.realm === "HOMOLOGATION") {
    if (user.token) return <ConnectedHomologation user={user} onLogout={logout} />;
    return (
      <HomologationApp
        user={user}
        onLogout={logout}
        onUser={(next) => enter(next, false)}
      />
    );
  }
  return (
    <>
      <a className="skip-link" href="#main-content" onClick={(event) => {
        event.preventDefault();
        document.getElementById("main-content")?.focus();
      }}>
        Pular para conteúdo
      </a>
      {menu && (
        <button
          className="nav-backdrop"
          aria-label="Fechar menu"
          onClick={() => setMenu(false)}
        />
      )}
      <aside id="panel-sidebar" className={menu ? "sidebar open" : "sidebar"} onKeyDown={(event) => {
        if (event.key === "Escape") {
          setMenu(false);
          document.getElementById("panel-menu-toggle")?.focus();
        }
      }}>
        <a className="brand" href="#overview" aria-label="InPay início" onClick={(event) => { event.preventDefault(); if (activeProduct) switchProduct(activeProduct.id); }}>
          <span className="brand-symbol">in</span>InPay
          <span className="brand-dot">.</span>
        </a>
        <div className="workspace">
          <span className="workspace-logo">IL</span>
          <label>
            Aplicação
            <select aria-label="Aplicação InPay" value={activeProduct?.id || ""} onChange={(event) => switchProduct(event.target.value)}>
              {!activeProduct && <option value="">Sem aplicações</option>}
              {availableProducts.map((product) => <option key={product.id} value={product.id}>{product.label}</option>)}
            </select>
          </label>
        </div>
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
          <button className="profile" onClick={() => navigate("profile")}>
            <span>IL</span>
            <div>
              <strong>{user?.name || "Conta"}</strong>
              <small>{user?.role} · {user?.stores?.find((item: { id: string }) => item.id === session.tenant)?.name || "Loja"}</small>
            </div>
            <PanelLeftClose size={16} />
          </button>
          <button onClick={logout}>Sair</button>
        </div>
      </aside>
      <div className="shell">
        <header className="topbar">
          <div className="breadcrumbs">
            <button
              id="panel-menu-toggle"
              className="icon-button mobile-menu"
              aria-label="Abrir menu"
              aria-expanded={menu}
              aria-controls="panel-sidebar"
              onClick={() => setMenu(true)}
            >
              <Menu />
            </button>
            <span>{activeProduct?.label || "Painel gerencial"}</span>
            <span>/</span>
            <strong>{String(activeName)}</strong>
            {user?.stores?.length > 1 && (
              <select
                aria-label="Loja"
                value={session.tenant}
                onChange={(event) =>
                  setSession((current) => ({ ...current, tenant: event.target.value }))
                }
              >
                {user.stores.map((item: { id: string; name: string }) => (
                  <option value={item.id} key={item.id}>
                    {item.name}
                  </option>
                ))}
              </select>
            )}
          </div>
          <div className="topbar-actions">
            <div className="global-search">
              <Search size={16} />
              <input
                aria-label="Buscar páginas"
                placeholder="Buscar no painel"
                value={search}
                onChange={(e) => setSearch(e.target.value)}
                onKeyDown={(event) => {
                  if (event.key === "Escape") setSearch("");
                  if (event.key === "Enter" && searchQuery && searchResults.length > 0) {
                    navigate(String(searchResults[0][0]));
                  }
                }}
              />
              {searchQuery && (
                <div className="search-results">
                  {searchResults.length === 0 && <p role="status">Nenhuma página encontrada.</p>}
                  {searchResults.map((i) => (
                      <button
                        key={String(i[0])}
                        onClick={() => navigate(String(i[0]))}
                      >
                        {String(i[1])}
                        {productFor(String(i[0])) && <small>{productFor(String(i[0]))?.label}</small>}
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
