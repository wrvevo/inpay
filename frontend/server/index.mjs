import { createServer } from "node:http";
import { readFile } from "node:fs/promises";
import { resolve, extname, sep } from "node:path";
import { fileURLToPath } from "node:url";
import {
  permittedOrigin,
  resolveRoute,
  upstreamAuthorization,
} from "./routes.mjs";

const dist = fileURLToPath(new URL("../dist/", import.meta.url));
const mime = {
  ".html": "text/html; charset=utf-8",
  ".js": "text/javascript; charset=utf-8",
  ".css": "text/css; charset=utf-8",
  ".svg": "image/svg+xml",
  ".json": "application/json",
};

/** Bounded reads avoid unbounded buffering of incoming or upstream data. */
async function readBounded(stream, maximum) {
  const chunks = [];
  let size = 0;
  for await (const chunk of stream) {
    size += chunk.length;
    if (size > maximum) throw new Error("Body limit");
    chunks.push(Buffer.from(chunk));
  }
  return Buffer.concat(chunks);
}
function json(response, status, data) {
  response.writeHead(status, {
    "Content-Type": "application/json",
    "Cache-Control": "no-store",
  });
  response.end(JSON.stringify(data));
}

/** Only links, transaction reads and risk assessments are exposed; no raw card/provider operations. */
export function createPanelServer(env = process.env, fetchUpstream = fetch) {
  return createServer(async (request, response) => {
    response.setHeader("X-Content-Type-Options", "nosniff");
    response.setHeader("Referrer-Policy", "no-referrer");
    response.setHeader("X-Frame-Options", "DENY");
    response.setHeader(
      "Content-Security-Policy",
      "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline' https://fonts.googleapis.com; font-src 'self' https://fonts.gstatic.com; img-src 'self' data:; connect-src 'self'; frame-ancestors 'none'; base-uri 'none'; form-action 'self'",
    );
    try {
      const url = new URL(request.url, "http://panel.local");
      if (url.pathname === "/healthz" && request.method === "GET")
        return json(response, 200, { status: "UP" });
      if (url.pathname.startsWith("/api/")) {
        if (!permittedOrigin(request.headers, env))
          return json(response, 403, { error: "Origin denied" });
        if (url.search)
          return json(response, 400, { error: "Unexpected query" });
        const target = resolveRoute(request.method, url.pathname, env);
        if (!target) return json(response, 404, { error: "Route unavailable" });
        const onboarding = url.pathname.startsWith("/api/painel/homologation/v2/");
        if (onboarding && !request.headers.authorization?.startsWith("Bearer "))
          return json(response, 401, { error: "Named operator required" });
        const documentUpload = onboarding && /\/cases\/[0-9a-f-]+\/documents$/.test(url.pathname);
        const documentDownload = onboarding && /\/documents\/[0-9a-f-]+\/download$/.test(url.pathname);
        let authorization = upstreamAuthorization(request.headers, env);
        // Fixed fixture identity for the LOCAL customer portal only. JWTs are never replaced.
        if (
          !request.headers.authorization &&
          env.PANEL_AUTH_MODE === "local" &&
          env.LOCAL_PASSWORD &&
          /^\/api\/credits\/me(?:\/|$)/.test(url.pathname)
        ) {
          authorization = `Basic ${Buffer.from(`customer:${env.LOCAL_PASSWORD}`).toString("base64")}`;
        }
        if (!authorization)
          return json(response, 401, { error: "Authentication required" });
        const tenant = request.headers["x-merchant-id"];
        if (
          tenant &&
          !/^[\da-f]{8}-[\da-f]{4}-[\da-f]{4}-[\da-f]{4}-[\da-f]{12}$/i.test(
            tenant,
          )
        )
          return json(response, 400, { error: "Invalid tenant" });
        let body;
        if (["POST", "PUT"].includes(request.method)) {
          if (!request.headers["content-type"]?.startsWith("application/json"))
            return json(response, 415, { error: "JSON required" });
          body = await readBounded(request, documentUpload ? 3 * 1024 * 1024 : 32768);
          try {
            JSON.parse(body.toString());
          } catch {
            return json(response, 400, { error: "Invalid JSON" });
          }
        }
        const upstream = await fetchUpstream(target, {
          method: request.method,
          body,
          redirect: "error",
          signal: AbortSignal.timeout(onboarding ? 90000 : 12000),
          headers: {
            Authorization: authorization,
            "Content-Type": "application/json",
            ...(tenant ? { "X-Merchant-Id": tenant } : {}),
            ...(request.headers["idempotency-key"]
              ? { "Idempotency-Key": request.headers["idempotency-key"] }
              : {}),
          },
        });
        if (!upstream.ok) {
          await upstream.body?.cancel();
          return json(response, upstream.status, {
            error: "Upstream operation failed",
            status: upstream.status,
          });
        }
        const bytes = upstream.body
          ? await readBounded(upstream.body, documentDownload ? 3 * 1024 * 1024 : 1048576)
          : Buffer.alloc(0);
        const data = bytes.length ? JSON.parse(bytes.toString()) : {};
        return json(response, upstream.status, data);
      }
      if (request.method !== "GET" && request.method !== "HEAD")
        return json(response, 405, { error: "Method unavailable" });
      const requested =
        url.pathname === "/"
          ? "index.html"
          : decodeURIComponent(url.pathname).slice(1);
      const filename = resolve(dist, requested);
      if (!filename.startsWith(resolve(dist) + sep))
        return json(response, 404, { error: "Not found" });
      let contents;
      try {
        contents = await readFile(filename);
      } catch {
        return json(response, 404, { error: "Not found" });
      }
      response.writeHead(200, {
        "Content-Type": mime[extname(filename)] || "application/octet-stream",
        "Cache-Control": filename.endsWith("index.html")
          ? "no-store"
          : "public, max-age=3600",
      });
      response.end(request.method === "HEAD" ? undefined : contents);
    } catch {
      // Never log tokens, upstream bodies or credentials.
      json(response, 502, {
        error: "Service unavailable; verify configuration and upstream health",
      });
    }
  });
}
if (
  process.argv[1] &&
  resolve(process.argv[1]) === fileURLToPath(import.meta.url)
) {
  createPanelServer().listen(
    Number(process.env.PORT || 8080),
    process.env.HOST || "127.0.0.1",
    () => console.log("InPay panel ready"),
  );
}
