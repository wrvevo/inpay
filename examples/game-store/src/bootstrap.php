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
function loadDemo(): void {
    $id = $_GET["demo"] ?? ($_SESSION["demo_id"] ?? null);
    if ($id === null) return;
    if (!is_string($id) || !preg_match('/^[0-9a-f-]{36}$/D', $id) || getenv("STORE_MODE") !== "local-simulator") {
        http_response_code(403); exit("Demo indisponível");
    }
    $base = getenv("INPAY_ONBOARDING_URL");
    if (!$base) { http_response_code(503); exit("Homologação indisponível"); }
    $headers = "Authorization: " . getenv("INPAY_AUTHORIZATION") . "\r\nX-Merchant-Id: " . getenv("INPAY_TENANT");
    $ctx = stream_context_create(["http"=>["method"=>"GET","header"=>$headers,"timeout"=>5,"follow_location"=>0]]);
    $raw = @file_get_contents(rtrim($base,"/")."/homologation/v2/demo/".$id,false,$ctx);
    $config = $raw === false ? null : json_decode($raw,true);
    if (!is_array($config) || ($config["demoId"]??null)!==$id || ($config["mode"]??null)!=="SYNTHETIC" || ($config["productionEnabled"]??true)!==false || !is_array($config["bindings"]??null)) {
        http_response_code(403); exit("Demo não liberada");
    }
    if (($_SESSION["demo_id"]??null)!==$id) {
        session_regenerate_id(true); $_SESSION["cart"]=[]; unset($_SESSION["checkout_key"],$_SESSION["admin"]); $_SESSION["owner"]=bin2hex(random_bytes(16));
    }
    $_SESSION["demo_id"]=$id; $_SESSION["demo_config"]=$config;
}
loadDemo();

function repository(callable $work): mixed
{
    $path = getenv("STORE_DATA") ?: dirname(__DIR__) . "/data/store.json";
    if (isset($_SESSION["demo_id"])) $path=dirname($path)."/demo-".$_SESSION["demo_id"].".json";
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
/**
 * Meios habilitados na loja. A chave visível dos cinco meios originais é o contrato da jornada.
 * Adquirentes e bancos usam a mesma jornada do gateway; o stub X-Mock dos conectores não muda.
 */
function paymentMethods(): array
{
    $methods = [
        "credit_card" => ["Cartão de crédito", "credit_card"],
        "debit_card" => ["Cartão de débito", "debit_card"],
        "pix" => ["Pix", "pix"],
        "boleto" => ["Boleto", "boleto"],
        "transfer" => ["Transferência bancária", "transfer"],
    ];
    if (isset($_SESSION["demo_config"])) {
        $bindings=$_SESSION["demo_config"]["bindings"]; $out=[];
        foreach ($methods as $key=>$method) if(isset($bindings[$key])) $out[$key]=[$method[0]." · ".$bindings[$key],$method[1]];
        return $out;
    }
    foreach (
        [
            "cielo" => "Cielo",
            "rede" => "Rede",
            "getnet" => "Getnet",
            "stone" => "Stone",
            "bin" => "Bin",
            "global_payments" => "Global Payments",
            "pagbank" => "PagBank",
            "safrapay" => "SafraPay",
        ]
        as $id => $name
    ) {
        $methods[$id] = [$name . " · cartão de crédito", "credit_card"];
    }
    foreach (
        [
            "c6" => "C6 Bank",
            "itau" => "Itaú",
            "santander" => "Santander",
            "bb" => "Banco do Brasil",
            "bradesco" => "Bradesco",
            "caixa" => "Caixa",
            "banrisul" => "Banrisul",
        ]
        as $id => $name
    ) {
        $methods[$id . "_pix"] = [$name . " · Pix", "pix"];
        $methods[$id . "_boleto"] = [$name . " · boleto", "boleto"];
    }
    return $methods;
}
/** Cliente REST: credencial permanece no servidor; nunca recebe PAN/CVV. */
function gateway(
    string $method,
    string $path,
    ?array $body = null,
    string $key = "",
): array {
    $base = rtrim(getenv("INPAY_URL") ?: "http://127.0.0.1:8084", "/");
    $auth =
        getenv("INPAY_AUTHORIZATION") ?:
        "Basic aW5wYXk6aW5wYXlAMTIz";
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
/** Envia agregados do checkout ao InPay Risk. Falha aqui não desfaz o pedido. */
function notifyFraud(int $amount, array $post): void
{
    $base = getenv("INPAY_FRAUD_URL") ?: "";
    if ($base === "") {
        return;
    }
    $typing = filter_var($post["typing_ms"] ?? 0, FILTER_VALIDATE_INT);
    $pointer = filter_var($post["pointer_entropy"] ?? 0, FILTER_VALIDATE_INT);
    $dwell = filter_var($post["dwell_seconds"] ?? 0, FILTER_VALIDATE_INT);
    $typing = is_int($typing) ? max(0, min(60000, $typing)) : 0;
    $pointer = is_int($pointer) ? max(0, min(100, $pointer)) : 0;
    $dwell = is_int($dwell) ? max(0, min(86400, $dwell)) : 0;
    $bytes = random_bytes(16);
    $bytes[6] = chr((ord($bytes[6]) & 0x0f) | 0x40);
    $bytes[8] = chr((ord($bytes[8]) & 0x3f) | 0x80);
    $hex = bin2hex($bytes);
    $paymentId = substr($hex, 0, 8) . "-" . substr($hex, 8, 4) . "-" . substr($hex, 12, 4) . "-" . substr($hex, 16, 4) . "-" . substr($hex, 20);
    $body = [
        "paymentId" => $paymentId,
        "amount" => max(1, $amount),
        "attemptsLastHour" => 1,
        "declinesLastHour" => 0,
        "knownCompromised" => false,
    ];
    if ($typing > 0 || $pointer > 0 || $dwell > 0) {
        $body["behavior"] = [
            "typingIntervalMs" => $typing,
            "mouseEntropy" => $pointer,
            "navigationSeconds" => $dwell,
            "unusualJourney" => false,
        ];
    }
    $auth = getenv("INPAY_AUTHORIZATION") ?: "Basic aW5wYXk6aW5wYXlAMTIz";
    $context = stream_context_create([
        "http" => [
            "method" => "POST",
            "header" => "Content-Type: application/json\r\nAuthorization: $auth\r\nX-Merchant-Id: " .
                (getenv("INPAY_TENANT") ?: "11111111-1111-1111-1111-111111111111") . "\r\n",
            "content" => json_encode($body),
            "timeout" => 2,
            "ignore_errors" => true,
        ],
    ]);
    @file_get_contents(rtrim($base, "/") . "/internal/v1/assessments", false, $context);
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
