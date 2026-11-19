const uuid =
  "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}";
const rules = [
  [
    "GET",
    new RegExp(`^/api/transactions/(${uuid})$`),
    "QUERY_URL",
    "http://127.0.0.1:8091",
    (id) => `/v1/transactions/${id}`,
  ],
  [
    "POST",
    /^\/api\/links$/,
    "LINKS_URL",
    "http://127.0.0.1:8082",
    () => "/v1/payment-links",
  ],
  [
    "GET",
    new RegExp(`^/api/links/(${uuid})$`),
    "LINKS_URL",
    "http://127.0.0.1:8082",
    (id) => `/v1/payment-links/${id}`,
  ],
  [
    "POST",
    new RegExp(`^/api/links/(${uuid})/revoke$`),
    "LINKS_URL",
    "http://127.0.0.1:8082",
    (id) => `/v1/payment-links/${id}/revoke`,
  ],
  [
    "POST",
    /^\/api\/fraud$/,
    "FRAUD_URL",
    "http://127.0.0.1:8087",
    () => "/internal/v1/assessments",
  ],
];

/** A fixed route catalog prevents callers from choosing upstream hosts or arbitrary API paths. */
export function resolveRoute(method, path, env = process.env) {
  for (const [allowed, pattern, key, fallback, build] of rules) {
    const match = path.match(pattern);
    if (method !== allowed || !match) continue;
    const base = new URL(env[key] || fallback);
    if (
      !["http:", "https:"].includes(base.protocol) ||
      base.username ||
      base.password ||
      base.search ||
      base.hash
    )
      throw new Error("Invalid service URL");
    if (env.PANEL_AUTH_MODE !== "local" && base.protocol !== "https:")
      throw new Error("HTTPS upstream required outside local mode");
    return new URL(build(match[1]), base).toString();
  }
  return null;
}

/** Never falls back to local Basic after a supplied bearer token fails upstream. */
export function upstreamAuthorization(headers, env = process.env) {
  if (headers.authorization) {
    if (!/^Bearer [A-Za-z0-9._~-]+$/.test(headers.authorization)) return null;
    return headers.authorization;
  }
  if (env.PANEL_AUTH_MODE === "local" && env.LOCAL_PASSWORD) {
    return `Basic ${Buffer.from(`developer:${env.LOCAL_PASSWORD}`).toString("base64")}`;
  }
  return null;
}

/** Same-origin checks protect the explicitly enabled local credential bridge. */
export function permittedOrigin(headers, env = process.env) {
  if (headers["sec-fetch-site"] === "cross-site") return false;
  const allowed = (
    env.PANEL_ORIGINS || "http://localhost:3080,http://127.0.0.1:3080"
  ).split(",");
  return !headers.origin || allowed.includes(headers.origin);
}
