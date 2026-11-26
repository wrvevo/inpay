import { useState } from "react";
import { Heading, Notice } from "../components/UI";
import { login, recover, resetPassword, PASSWORD } from "../access.mjs";

export function Login({ onLogin }: { onLogin: (user: any) => void }) {
  const [realm, setRealm] = useState("PANEL");
  const [mode, setMode] = useState<"enter" | "recover">("enter");
  const [error, setError] = useState("");
  const [token, setToken] = useState("");
  async function enter(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError("");
    const form = new FormData(event.currentTarget);
    const identity = String(form.get("login") || "");
    const password = String(form.get("password") || "");
    try {
      const remote = await fetch("/api/painel/auth/login", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ realm, login: identity, password }),
        signal: AbortSignal.timeout(2000),
      });
      if (remote.ok) {
        onLogin(await remote.json());
        return;
      }
      if (remote.status === 401) throw new Error("Credencial inválida.");
    } catch (failure) {
      if ((failure as Error).message === "Credencial inválida.") {
        setError((failure as Error).message);
        return;
      }
    }
    try {
      onLogin(login(realm, identity, password));
    } catch (failure) {
      setError((failure as Error).message);
    }
  }
  function sendReset(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError("");
    const form = new FormData(event.currentTarget);
    try {
      if (token) {
        resetPassword(String(form.get("token")), String(form.get("password")));
        setToken("");
        setMode("enter");
        setError("Senha atualizada. Entre com a nova senha.");
        return;
      }
      setToken(recover(String(form.get("login"))));
    } catch (failure) {
      setError((failure as Error).message);
    }
  }
  return (
    <main className="login-screen">
      <section className="card login-card">
        <Heading
          title={realm === "PANEL" ? "Entrar no InPay" : "Homologação de lojas"}
          subtitle="Use e-mail ou documento e a senha da sua conta."
        />
        <div className="form-grid">
          <button type="button" className={realm === "PANEL" ? "primary" : ""} onClick={() => setRealm("PANEL")}>
            Gateway
          </button>
          <button type="button" className={realm === "HOMOLOGATION" ? "primary" : ""} onClick={() => setRealm("HOMOLOGATION")}>
            Homologação
          </button>
        </div>
        {mode === "enter" ? (
          <form className="form" onSubmit={enter}>
            <label>
              E-mail ou documento
              <input name="login" required autoComplete="username" defaultValue="admin@inpay.local" />
            </label>
            <label>
              Senha
              <input name="password" type="password" required autoComplete="current-password" defaultValue={PASSWORD} />
            </label>
            {error && <p role="alert" className="error">{error}</p>}
            <button className="primary">Entrar</button>
            <button type="button" onClick={() => { setMode("recover"); setError(""); }}>
              Esqueci a senha
            </button>
          </form>
        ) : (
          <form className="form" onSubmit={sendReset}>
            <Notice>
              No laboratório o token de redefinição aparece nesta tela. Não há envio de e-mail.
            </Notice>
            <label>
              E-mail ou documento
              <input name="login" required defaultValue="viewer@inpay.local" />
            </label>
            {token && (
              <>
                <p>Token: <code>{token}</code></p>
                <label>
                  Token
                  <input name="token" required defaultValue={token} />
                </label>
                <label>
                  Nova senha
                  <input name="password" type="password" required minLength={8} />
                </label>
              </>
            )}
            {error && <p role="alert" className="error">{error}</p>}
            <button className="primary">{token ? "Redefinir senha" : "Gerar token"}</button>
            <button type="button" onClick={() => setMode("enter")}>Voltar</button>
          </form>
        )}
      </section>
    </main>
  );
}
