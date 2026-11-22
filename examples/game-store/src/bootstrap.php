<?php
declare(strict_types=1);
/** Infraestrutura local: sessão, catálogo e pedidos persistidos fora da pasta pública. */
session_set_cookie_params(["httponly" => true, "samesite" => "Strict"]);
if (!is_writable(ini_get("session.save_path"))) {
    session_save_path(sys_get_temp_dir());
}
session_start();
$_SESSION["csrf"] ??= bin2hex(random_bytes(24));
$_SESSION["owner"] ??= bin2hex(random_bytes(24));
$_SESSION["cart"] ??= [];
/** Escapa conteúdo dinâmico antes da renderização HTML. */
function escape(mixed $value): string
{
    return htmlspecialchars((string) $value, ENT_QUOTES, "UTF-8");
}
/** Formata centavos inteiros sem usar ponto flutuante na regra de negócio. */
function money(int $cents): string
{
    return 'R$ ' . number_format($cents / 100, 2, ",", ".");
}
/** Cria o catálogo demonstrativo de dez produtos sem licenças reais. */
function seed(): array
{
    $names = [
        ["Neon Horizon", 12990, "RPG", "◈"],
        ["Orbit Runner", 4990, "Arcade", "◎"],
        ["Legends of Aurora", 8990, "Aventura", "✦"],
        ["Cyber District", 15990, "Ação", "▧"],
        ["Pixel Garden", 2990, "Simulação", "❋"],
        ["Racing Beyond", 11990, "Corrida", "➜"],
        ["Deep Space", 6990, "Estratégia", "⌘"],
        ["Game Pass Indie • 30 dias", 2490, "Assinatura", "∞"],
        ["Cloud Arena • 90 dias", 5990, "Assinatura", "△"],
        ['InGame Credits • R$ 50', 5000, "Gift card", "▣"],
    ];
    $products = [];
    foreach ($names as $i => $p) {
        $products[] = [
            "id" => $i + 1,
            "name" => $p[0],
            "price" => $p[1],
            "category" => $p[2],
            "symbol" => $p[3],
            "active" => true,
        ];
    }
    return ["products" => $products, "orders" => []];
}
/** Lock de arquivo evita gravações concorrentes perdidas. Somente para laboratório local. */
function repository(callable $work): mixed
{
    $path = getenv("STORE_DATA") ?: dirname(__DIR__) . "/data/store.json";
    if (!is_dir(dirname($path))) {
        mkdir(dirname($path), 0700, true);
    }
    $file = fopen($path, "c+");
    if (!$file || !flock($file, LOCK_EX)) {
        throw new RuntimeException("Armazenamento indisponível");
    }
    try {
        $raw = stream_get_contents($file);
        $data = $raw
            ? json_decode($raw, true, 512, JSON_THROW_ON_ERROR)
            : seed();
        $result = $work($data);
        rewind($file);
        ftruncate($file, 0);
        fwrite(
            $file,
            json_encode($data, JSON_THROW_ON_ERROR | JSON_PRETTY_PRINT),
        );
        fflush($file);
        return $result;
    } finally {
        flock($file, LOCK_UN);
        fclose($file);
    }
}
/** Cliente REST: credencial permanece no servidor; nunca recebe PAN/CVV. */
function gateway(
    string $method,
    string $path,
    ?array $body = null,
    string $key = "",
): array {
    $base = rtrim(getenv("INPAY_URL") ?: "http://127.0.0.1:8084", "/");
    $auth = getenv("INPAY_AUTHORIZATION");
    if (!$auth) {
        throw new RuntimeException(
            "Configure INPAY_AUTHORIZATION no servidor da loja.",
        );
    }
    $headers =
        "Content-Type: application/json\r\nAuthorization: $auth\r\nX-Merchant-Id: " .
        (getenv("INPAY_TENANT") ?: "11111111-1111-1111-1111-111111111111") .
        "\r\n";
    if ($key !== "") {
        $headers .= "Idempotency-Key: $key\r\n";
    }
    $context = stream_context_create([
        "http" => [
            "method" => $method,
            "header" => $headers,
            "content" => $body === null ? "" : json_encode($body),
            "timeout" => 25,
            "ignore_errors" => true,
            "follow_location" => 0,
        ],
    ]);
    $raw = @file_get_contents(
        $base . "/gateway/v1/journeys" . $path,
        false,
        $context,
    );
    if (
        $raw === false ||
        !isset($http_response_header[0]) ||
        !preg_match("/\s200\s/", $http_response_header[0])
    ) {
        throw new RuntimeException(
            "InPay indisponível ou operação recusada. Consulte o pedido antes de tentar novamente.",
        );
    }
    $result = json_decode($raw, true, 512, JSON_THROW_ON_ERROR);
    if (!isset($result["id"], $result["status"])) {
        throw new RuntimeException("Resposta inválida do Gateway.");
    }
    return $result;
}
/** Gera campo com token de sessão para operações do formulário. */
function csrf(): string
{
    return '<input type="hidden" name="csrf" value="' .
        escape($_SESSION["csrf"]) .
        '">';
}
/** Finaliza POST com redirecionamento, evitando reenvio pelo navegador. */
function redirect(string $path = "/"): never
{
    header("Location: " . $path, true, 303);
    exit();
}
/** Bloqueia alterações de catálogo sem sessão administrativa. */
function requireAdmin(): void
{
    if (empty($_SESSION["admin"])) {
        http_response_code(403);
        throw new RuntimeException("Acesso administrativo necessário.");
    }
}
