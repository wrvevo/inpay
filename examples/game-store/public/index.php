<?php
declare(strict_types=1);
require dirname(__DIR__) . "/src/bootstrap.php";
$error = "";
$page = $_GET["page"] ?? "store";
try {
    if ($_SERVER["REQUEST_METHOD"] === "POST") {
        if (!hash_equals($_SESSION["csrf"], $_POST["csrf"] ?? "")) {
            http_response_code(403);
            throw new RuntimeException("Sessão expirada. Atualize a página.");
        }
        $action = $_POST["action"] ?? "";
        if ($action === "login") {
            $password = getenv("STORE_ADMIN_PASSWORD") ?: "inpay@123";
            if (!hash_equals($password, $_POST["password"] ?? "")) {
                throw new RuntimeException("Credencial inválida.");
            }
            session_regenerate_id(true);
            $_SESSION["admin"] = true;
            redirect("/?page=admin");
        }
        if ($action === "logout") {
            unset($_SESSION["admin"]);
            redirect();
        }
        if ($action === "add") {
            $id = (int) ($_POST["product"] ?? 0);
            $valid = repository(
                fn(&$d) => array_filter(
                    $d["products"],
                    fn($p) => $p["id"] === $id && $p["active"],
                ),
            );
            if (!$valid) {
                throw new RuntimeException("Produto indisponível");
            }
            $_SESSION["cart"][$id] = min(10, ($_SESSION["cart"][$id] ?? 0) + 1);
            unset($_SESSION["checkout_key"]);
            redirect("/?page=checkout");
        }
        if ($action === "clear") {
            $_SESSION["cart"] = [];
            unset($_SESSION["checkout_key"]);
            redirect("/?page=checkout");
        }
        if ($action === "product") {
            requireAdmin();
            $price = filter_var($_POST["price"] ?? "", FILTER_VALIDATE_INT);
            if ($price === false || $price < 1 || $price > 10000000) {
                throw new RuntimeException("Preço inválido em centavos.");
            }
            repository(function (&$d) use ($price) {
                foreach ($d["products"] as &$p) {
                    if ($p["id"] === (int) $_POST["product"]) {
                        $p["price"] = $price;
                        $p["active"] = isset($_POST["active"]);
                    }
                }
            });
            redirect("/?page=admin");
        }
        if ($action === "checkout") {
            $method = $_POST["method"] ?? "";
            $catalog = paymentMethods();
            if (!isset($catalog[$method])) {
                throw new RuntimeException("Meio inválido");
            }
            $journeyMethod = $catalog[$method][1];
            $key = $_POST["checkout_key"] ?? "";
            if (
                !isset($_SESSION["checkout_key"]) ||
                !hash_equals($_SESSION["checkout_key"], $key)
            ) {
                throw new RuntimeException("Checkout inválido.");
            }
            $instrument =
                getenv("STORE_MODE") === "cielo-sandbox"
                    ? trim($_POST["instrument"] ?? "sandbox-approved")
                    : "fixture-approved";
            $prepared = repository(function (&$d) use (
                $method,
                $journeyMethod,
                $key,
            ) {
                foreach ($d["orders"] as $o) {
                    if ($o["id"] === $key) {
                        return ["order" => $o, "created" => false];
                    }
                }
                $items = [];
                $total = 0;
                foreach ($d["products"] as $p) {
                    $q = (int) ($_SESSION["cart"][$p["id"]] ?? 0);
                    if ($q > 0) {
                        if (!$p["active"]) {
                            throw new RuntimeException("Produto desativado");
                        }
                        $items[] = [
                            "name" => $p["name"],
                            "amount" => $p["price"],
                            "quantity" => $q,
                        ];
                        $total += $p["price"] * $q;
                    }
                }
                if (!$items) {
                    throw new RuntimeException("Carrinho vazio");
                }
                $order = [
                    "id" => $key,
                    "owner" => $_SESSION["owner"],
                    "items" => $items,
                    "amount" => $total,
                            "provider" => $method,
                            "method" => $journeyMethod,
                    "status" => "PROCESSING",
                    "journey_id" => null,
                    "artifacts" => [],
                    "created_at" => gmdate(DATE_ATOM),
                ];
                $d["orders"][] = $order;
                return ["order" => $order, "created" => true];
            });
            if ($prepared["created"]) {
                try {
                    $result = gateway(
                        "POST",
                        "",
                        [
                            "merchantOrder" => $key,
                            "amount" => $prepared["order"]["amount"],
                            "method" => $journeyMethod,
                            "instrument" => $instrument,
                        ],
                        $key,
                    );
                    try {
                        notifyFraud((int) $prepared["order"]["amount"], $_POST);
                    } catch (Throwable) {
                    }
                    repository(function (&$d) use ($key, $result) {
                        foreach ($d["orders"] as &$o) {
                            if ($o["id"] === $key) {
                                $o["journey_id"] = $result["id"];
                                $o["status"] = $result["status"];
                                $o["artifacts"] = $result["artifacts"] ?? [];
                            }
                        }
                    });
                } catch (Throwable $e) {
                    repository(function (&$d) use ($key) {
                        foreach ($d["orders"] as &$o) {
                            if ($o["id"] === $key) {
                                $o["status"] = "UNKNOWN";
                            }
                        }
                    });
                    throw $e;
                }
            }
            $_SESSION["cart"] = [];
            redirect("/?page=orders");
        }
        if (in_array($action, ["refresh", "cancel"], true)) {
            $id = $_POST["order"] ?? "";
            $order = repository(function (&$d) use ($id) {
                foreach ($d["orders"] as $o) {
                    if (
                        $o["id"] === $id &&
                        ($o["owner"] === $_SESSION["owner"] ||
                            !empty($_SESSION["admin"]))
                    ) {
                        return $o;
                    }
                }
                throw new RuntimeException("Pedido não encontrado");
            });
            if (!$order["journey_id"]) {
                $recovered = gateway(
                    "GET",
                    "/by-key/" . rawurlencode($order["id"]),
                );
                $order["journey_id"] = $recovered["id"];
                repository(function (&$d) use ($id, $recovered) {
                    foreach ($d["orders"] as &$o) {
                        if ($o["id"] === $id) {
                            $o["journey_id"] = $recovered["id"];
                        }
                    }
                });
            }
            $result = gateway(
                "POST",
                "/" . $order["journey_id"] . "/" . $action,
                [],
            );
            repository(function (&$d) use ($id, $result) {
                foreach ($d["orders"] as &$o) {
                    if ($o["id"] === $id) {
                        $o["status"] = $result["status"];
                        $o["artifacts"] = $result["artifacts"] ?? [];
                    }
                }
            });
            redirect(
                "/?page=" . (!empty($_SESSION["admin"]) ? "admin" : "orders"),
            );
        }
    }
} catch (Throwable $e) {
    $error = $e->getMessage();
}
$data = repository(fn(&$d) => $d);
$cartCount = array_sum($_SESSION["cart"]);
$isAdmin = !empty($_SESSION["admin"]);
$orders = array_filter(
    $data["orders"],
    fn($o) => $isAdmin || $o["owner"] === $_SESSION["owner"],
);
function orderTable(array $orders): void
{
    ?>
<div class="table"><table><thead><tr><th>Pedido</th><th>Itens</th><th>Valor</th><th>Meio / estado</th><th>Operações</th></tr></thead><tbody>
<?php foreach (array_reverse($orders) as $o): ?><tr><td><code><?= escape(
    substr($o["id"], 0, 12),
) ?></code><small><?= escape($o["created_at"]) ?></small></td><td><?= escape(
    implode(", ", array_column($o["items"], "name")),
) ?></td><td><?= money($o["amount"]) ?></td><td><?= escape(
    $o["method"],
) ?><br><span class="status"><?= escape(
    $o["status"],
) ?></span></td><td><form method="post"><?= csrf() ?><input type="hidden" name="order" value="<?= escape(
    $o["id"],
) ?>"><button name="action" value="refresh">Consultar provedor</button><button class="secondary" name="action" value="cancel">Cancelar</button></form></td></tr>
<?php if (
    $o["artifacts"]
): ?><tr><td colspan="5"><details><summary>Instruções de pagamento</summary><?php foreach (
    $o["artifacts"]
    as $k => $v
): ?><p><strong><?= escape($k) ?></strong><code class="artifact"><?= escape(
    $v,
) ?></code></p><?php endforeach; ?></details></td></tr><?php endif;endforeach; ?></tbody></table></div>
<?php if (
    !$orders
): ?><p class="empty">Seus pedidos aparecerão aqui após o checkout.</p><?php endif;
}
?><!doctype html><html lang="pt-BR"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1"><title>InPay · Loja Magento</title><link rel="stylesheet" href="/style.css"></head><body>
<div class="lab">LABORATÓRIO INPAY <span>Produtos fictícios · <?= getenv(
    "STORE_MODE",
) === "cielo-sandbox"
    ? "Cielo sandbox · cartão fictício de teste"
    : "Simulação local · nenhum dinheiro movimentado" ?></span></div>
<header><a class="brand" href="/">in<span>pay</span><small>MAGENTO</small></a><nav><a href="/">Loja</a><a href="/?page=orders">Pedidos</a><a href="/admin">Painel</a><a href="<?= escape(
    getenv("INPAY_SWAGGER_URL") ?: "http://localhost:8084/swagger-ui/index.html",
) ?>" target="_blank" rel="noreferrer">Swagger</a><a class="cart" href="/?page=checkout">Carrinho <b><?= $cartCount ?></b></a></nav></header>
<main><?php if ($error): ?><div role="alert" class="error"><?= escape(
    $error,
) ?></div><?php endif; ?>
<?php if ($page === "store"): ?>
<section class="hero"><div><span class="eyebrow">SEU PRÓXIMO UNIVERSO COMEÇA AQUI</span><h1>Play beyond.<br><em>Explore mais.</em></h1><p>Jogos, assinaturas e novas histórias.<br>Uma loja de demonstração conectada ao InPay.</p><a class="button" href="#catalog">Descobrir os jogos ↗</a><div class="chips"><span>10 produtos</span><span><?= count(
    paymentMethods(),
) ?> meios no gateway</span><span>Jornada rastreável</span></div></div><div class="hero-art"><div class="orb">✦</div><span>NEON HORIZON</span><small>Uma nova dimensão para explorar</small></div></section>
<section id="catalog"><div class="section-title"><div><span class="eyebrow">SELEÇÃO INPLAY</span><h2>Encontre sua próxima aventura</h2></div><span>Catálogo demonstrativo</span></div><div class="products"><?php foreach (
    $data["products"]
    as $i => $p
):
    if (!$p["active"]) {
        continue;
    } ?><article><div class="cover color-<?= $i % 5 ?>"><span><?= escape(
    $p["category"],
) ?></span><b><?= escape($p["symbol"]) ?></b><strong><?= escape(
    $p["name"],
) ?></strong></div><div class="product-body"><small><?= escape(
    $p["category"],
) ?> · Digital</small><h3><?= escape(
     $p["name"],
 ) ?></h3><div class="buy"><strong><?= money(
    $p["price"],
) ?></strong><form method="post"><?= csrf() ?><input type="hidden" name="product" value="<?= $p[
    "id"
] ?>"><button name="action" value="add" aria-label="Adicionar <?= escape(
    $p["name"],
) ?>">Adicionar +</button></form></div></div></article><?php
endforeach; ?></div></section>
<?php elseif ($page === "checkout"):

    $_SESSION["checkout_key"] ??= bin2hex(random_bytes(16));
    $total = 0;
    ?>
<span class="eyebrow">CHECKOUT</span><h1>Pronto para jogar?</h1><div class="checkout"><section class="box"><h2>Seu carrinho</h2><?php foreach (
    $data["products"]
    as $p
):

    $q = $_SESSION["cart"][$p["id"]] ?? 0;
    if (!$q) {
        continue;
    }
    $total += $q * $p["price"];
    ?><div class="line"><span><?= escape(
    $p["name"],
) ?> × <?= $q ?></span><b><?= money($q * $p["price"]) ?></b></div><?php
endforeach; ?><div class="line total"><span>Total</span><b><?= money(
    $total,
) ?></b></div><form method="post"><?= csrf() ?><button class="secondary" name="action" value="clear">Limpar carrinho</button></form></section><section class="box"><h2>Como você quer pagar?</h2><form method="post" id="checkout"><?= csrf() ?><input type="hidden" name="checkout_key" value="<?= escape(
    $_SESSION["checkout_key"],
) ?>"><?php
foreach (
    paymentMethods() as $value => [$label]
): ?><label class="payment"><input type="radio" name="method" value="<?= escape(
    $value,
) ?>" required <?= $value === "pix"
    ? "checked"
    : "" ?>><?= escape($label) ?></label><?php endforeach;
if (
    getenv("STORE_MODE") === "cielo-sandbox"
): ?><input type="hidden" name="instrument" value="sandbox-approved"><p>Cartão fictício de teste Cielo. Este modo requer habilitação explícita das fixtures no Gateway.</p><?php endif;
?><input type="hidden" name="typing_ms" value="0"><input type="hidden" name="pointer_entropy" value="0"><input type="hidden" name="dwell_seconds" value="0"><script>(function(){var form=document.getElementById("checkout");if(!form)return;var start=Date.now(),last=0,gaps=[],moves=0;form.addEventListener("keydown",function(){var now=Date.now();if(last)gaps.push(now-last);last=now;});form.addEventListener("mousemove",function(){moves++;});form.addEventListener("submit",function(){var median=0;if(gaps.length){gaps.sort(function(a,b){return a-b;});median=gaps[Math.floor(gaps.length/2)];}form.typing_ms.value=String(median);form.pointer_entropy.value=String(Math.min(100,moves));form.dwell_seconds.value=String(Math.min(86400,Math.floor((Date.now()-start)/1000)));});})();</script><p class="muted">Não informe dados reais de cartão. Assinaturas são produtos demonstrativos; este checkout realiza uma cobrança única.</p><button class="primary" name="action" value="checkout" <?= $total ===
0
    ? "disabled"
    : "" ?>>Fechar pedido · <?= money($total) ?></button></form></section></div>
<?php
elseif (
    $page === "admin"
): ?><span class="eyebrow">CONTROLE DA LOJA</span><h1>Painel administrativo</h1><?php if (
    !$isAdmin
): ?><form class="box login" method="post"><?= csrf() ?><label>Senha administrativa<input type="password" name="password" required autocomplete="current-password"></label><button name="action" value="login">Entrar</button><p>Configurada por STORE_ADMIN_PASSWORD no servidor.</p></form><?php else: ?><div class="metrics"><div><small>Pedidos</small><strong><?= count(
    $orders,
) ?></strong></div><div><small>Pagos confirmados</small><strong><?= count(
    array_filter($orders, fn($o) => $o["status"] === "PAID"),
) ?></strong></div><div><small>Catálogo</small><strong><?= count(
    $data["products"],
) ?> produtos</strong></div></div><h2>Meios de pagamento</h2><p class="muted">Todos habilitados e integrados ao gateway em <code>/gateway/v1/journeys</code>. Os conectores conservam os stubs de <code>X-Mock: true</code>.</p><div class="admin-products"><?php foreach (
    paymentMethods() as $id => [$label, $journey]
): ?><article class="box"><strong><?= escape(
    $label,
) ?></strong><p>Habilitado · jornada <code><?= escape(
    $journey,
) ?></code></p></article><?php endforeach; ?></div><h2>Pedidos e cancelamentos</h2><?php orderTable(
     $orders,
 ); ?><h2>Gerenciar catálogo</h2><div class="admin-products"><?php foreach (
    $data["products"]
    as $p
): ?><form class="box" method="post"><?= csrf() ?><input type="hidden" name="product" value="<?= $p[
    "id"
] ?>"><strong><?= escape(
    $p["name"],
) ?></strong><label>Preço em centavos<input type="number" name="price" value="<?= $p[
    "price"
] ?>" min="1" required></label><label><input type="checkbox" name="active" <?= $p[
    "active"
]
    ? "checked"
    : "" ?>> Disponível</label><button name="action" value="product">Salvar produto</button></form><?php endforeach; ?></div><form method="post"><?= csrf() ?><button name="action" value="logout">Sair da administração</button></form><?php endif; ?>
<?php else: ?><span class="eyebrow">SUA JORNADA</span><h1>Meus pedidos</h1><p class="muted">Consulte o provedor para atualizar pagamentos pendentes. A loja não aprova pagamentos por conta própria.</p><?php orderTable(
    $orders,
);endif; ?>
</main><footer><b>inplay / inpay</b><span>Ambiente de integração · Jogos fictícios, sem entrega de licenças</span></footer></body></html>
