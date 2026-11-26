import { useState } from "react";
import { Heading, Notice } from "../components/UI";
import { createUser, setMenus, usersOf, MENUS } from "../access.mjs";

type TeamMember = { id: string; role: string; name: string; email: string; menus: string[]; stores: { id: string }[] };

export function TeamAccess({ user, onUser }: { user: TeamMember; onUser: (user: TeamMember) => void }) {
  const [rows, setRows] = useState<TeamMember[]>(() => usersOf(user));
  const [error, setError] = useState("");
  const ceiling: string[] = user.role === "ADMIN" ? MENUS : user.menus;
  function refresh(nextActor = user) {
    setRows(usersOf(nextActor));
  }
  function create(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError("");
    const form = new FormData(event.currentTarget);
    try {
      createUser(user, {
        role: String(form.get("role")),
        name: String(form.get("name")),
        email: String(form.get("email")),
        document: String(form.get("document")),
        menus: ceiling.filter((item) => form.get(`menu-${item}`) === "on"),
        stores: user.stores.map((item: { id: string }) => item.id),
      });
      refresh();
      event.currentTarget.reset();
    } catch (failure) {
      setError((failure as Error).message);
    }
  }
  if (!user.menus.includes("team") && user.role !== "ADMIN") {
    return <Notice>Seu perfil não recebe o menu de equipe.</Notice>;
  }
  return (
    <>
      <Heading
        title="Equipe e permissões"
        subtitle="Admin libera menus do gerente. O gerente só repassa o que já recebeu."
      />
      <div className="two-col">
        <section className="card">
          <h2>Pessoas da conta</h2>
          <ul className="rule-list">
            {rows.map((item) => (
              <li key={item.id}>
                <strong>{item.name}</strong> · {item.role}
                <small> {item.email} · menus: {item.menus.join(", ") || "—"}</small>
              </li>
            ))}
          </ul>
        </section>
        <section className="card">
          <h2>{user.role === "VIEWER" ? "Consulta" : "Novo acesso"}</h2>
          {user.role === "VIEWER" ? (
            <Notice>Consulta não cria usuários.</Notice>
          ) : (
            <form className="form" onSubmit={create}>
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
                <input name="document" required />
              </label>
              <label>
                Perfil
                <select name="role" defaultValue="VIEWER">
                  <option value="VIEWER">VIEWER</option>
                  <option value="MANAGER">MANAGER</option>
                  {user.role === "ADMIN" && <option value="ADMIN">ADMIN</option>}
                </select>
              </label>
              <fieldset>
                <legend>Menus que esta pessoa poderá abrir</legend>
                {ceiling.map((item) => (
                  <label className="check" key={item}>
                    <input name={`menu-${item}`} type="checkbox" defaultChecked={item === "overview"} />
                    {item}
                  </label>
                ))}
              </fieldset>
              {error && <p role="alert" className="error">{error}</p>}
              <button className="primary">Criar acesso</button>
            </form>
          )}
          {user.role !== "VIEWER" && rows.filter((item) => item.role !== "ADMIN" || user.role === "ADMIN").map((item) => (
            <form
              className="form"
              key={`menus-${item.id}`}
              onSubmit={(event) => {
                event.preventDefault();
                const form = new FormData(event.currentTarget);
                try {
                  const updated = setMenus(
                    user,
                    item.id,
                    ceiling.filter((menu) => form.get(menu) === "on"),
                  );
                  if (updated.id === user.id) onUser(updated);
                  refresh();
                  setError("");
                } catch (failure) {
                  setError((failure as Error).message);
                }
              }}
            >
              <h3>Menus de {item.name}</h3>
              {ceiling.map((menu) => (
                <label className="check" key={menu}>
                  <input name={menu} type="checkbox" defaultChecked={item.menus.includes(menu)} />
                  {menu}
                </label>
              ))}
              <button>Salvar menus</button>
            </form>
          ))}
        </section>
      </div>
    </>
  );
}
