export type Session = {
  mode: "demo" | "connected";
  token: string;
  tenant: string;
};

/** Credentials remain in component memory. Backends retain responsibility for scopes and ownership. */
export async function api(session: Session, path: string, body?: unknown) {
  const response = await fetch(`/api/${path}`, {
    method: body === undefined ? "GET" : "POST",
    headers: {
      "Content-Type": "application/json",
      ...(session.token ? { Authorization: `Bearer ${session.token}` } : {}),
      "X-Merchant-Id": session.tenant,
    },
    body: body === undefined ? undefined : JSON.stringify(body),
    signal: AbortSignal.timeout(15000),
  });
  const result = await response.json().catch(() => ({}));
  if (!response.ok) {
    const message =
      response.status === 401
        ? "Sessão ausente ou expirada. Atualize o access token."
        : response.status === 403
          ? "Sem permissão para esta operação ou lojista."
          : response.status === 404
            ? "Registro não encontrado."
            : response.status === 501
              ? "Operação ainda não disponível no backend."
              : "Não foi possível concluir. Verifique o serviço e tente novamente.";
    throw new Error(`${message} (HTTP ${response.status})`);
  }
  return result;
}

export const money = (cents: number) =>
  new Intl.NumberFormat("pt-BR", { style: "currency", currency: "BRL" }).format(
    cents / 100,
  );

/** Downloads only the explicitly supplied data and releases its temporary object URL. */
export function download(
  filename: string,
  contents: string,
  type = "text/plain",
) {
  const url = URL.createObjectURL(new Blob([contents], { type }));
  const anchor = document.createElement("a");
  anchor.href = url;
  anchor.download = filename;
  anchor.click();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}
