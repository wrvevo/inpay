package br.com.includelabs.inpay.payment;

import static org.junit.jupiter.api.Assertions.*;

import br.com.includelabs.inpay.payment.bootstrap.Application;
import com.sun.net.httpserver.HttpServer;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;
import org.junit.jupiter.api.*;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import tools.jackson.databind.json.JsonMapper;

/**
 * E2E HTTP: loja PHP → Gateway Spring/PostgreSQL → simulador de contrato do conector. Não é
 * homologação bancária.
 */
public class StoreJourneyIntegrationTest {
    static EmbeddedPostgres pg;
    static ConfigurableApplicationContext context;
    static HttpServer simulator;
    static Process shop;
    static Process cielo;
    static final boolean LIVE = "true".equals(System.getenv("CIELO_STORE_E2E"));
    static String base, store;
    static final JsonMapper json = JsonMapper.builder().build();
    static final Map<String, Map<String, Object>> payments = new ConcurrentHashMap<>();
    static final String auth =
            "Basic " + Base64.getEncoder().encodeToString("developer:integration-only".getBytes());
    static HttpClient client;

    @BeforeAll
    static void start() throws Exception {
        simulator = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        simulator.createContext(
                "/internal/v1/cielo/operations/",
                exchange -> {
                    if (!auth.equals(exchange.getRequestHeaders().getFirst("Authorization"))) {
                        exchange.sendResponseHeaders(401, -1);
                        exchange.close();
                        return;
                    }
                    String operation = exchange.getRequestURI().getPath().replaceAll(".*/", "");
                    Map<String, Object> result;
                    if (operation.equals("CREATE_SALE")) {
                        var body = json.readTree(exchange.getRequestBody().readAllBytes());
                        // Native connector accepts amount only inside Payment for CREATE_SALE.
                        if (body.has("amount")) {
                            exchange.sendResponseHeaders(422, -1);
                            exchange.close();
                            return;
                        }
                        var payment = body.path("payload").path("Payment");
                        String id = UUID.randomUUID().toString();
                        String method = payment.path("Type").asText();
                        var data = new HashMap<String, Object>();
                        data.put("PaymentId", id);
                        data.put("Amount", payment.path("Amount").asLong());
                        data.put("Status", method.endsWith("Card") ? 2 : 12);
                        if (method.equals("Pix"))
                            data.put("QrCodeString", "SIMULATED-NOT-A-REAL-PIX-" + id);
                        if (method.equals("Boleto"))
                            data.put("DigitableLine", "SIMULATED-NOT-A-REAL-BOLETO");
                        payments.put(id, data);
                        result = Map.of("Payment", data);
                    } else if (operation.equals("GET_PAYMENT")) {
                        String id =
                                exchange.getRequestURI()
                                        .getQuery()
                                        .substring("resourceId=".length());
                        var payment = payments.get(id);
                        if (payment.get("Status").equals(12)) payment.put("Status", 2);
                        result = Map.of("Payment", payment);
                    } else {
                        var body = json.readTree(exchange.getRequestBody().readAllBytes());
                        String id = body.path("resourceId").asText();
                        payments.get(id).put("Status", 10);
                        result = Map.of("PaymentId", id, "Status", 10);
                    }
                    byte[] bytes =
                            json.writeValueAsBytes(
                                    Map.of(
                                            "httpStatus",
                                            200,
                                            "uncertain",
                                            false,
                                            "replayed",
                                            false,
                                            "data",
                                            result));
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(200, bytes.length);
                    exchange.getResponseBody().write(bytes);
                    exchange.close();
                });
        simulator.start();
        pg =
                EmbeddedPostgres.builder()
                        .setPort(0)
                        .setServerConfig("unix_socket_directories", "")
                        .start();
        String connectorUrl = "http://127.0.0.1:" + simulator.getAddress().getPort();
        if (LIVE) connectorUrl = launchCielo();
        context =
                new SpringApplicationBuilder(Application.class)
                        .run(
                                "--spring.profiles.active=local",
                                "--server.port=0",
                                "--spring.datasource.url=jdbc:postgresql://localhost:"
                                        + pg.getPort()
                                        + "/postgres",
                                "--spring.datasource.username=postgres",
                                "--spring.datasource.password=",
                                "--inpay.local-password=integration-only",
                                "--spring.kafka.listener.auto-startup=false",
                                "--inpay.outbox.delay=3600000",
                                "--logging.level.root=WARN",
                                "--spring.main.banner-mode=off",
                                "--inpay.journey.connector-url=" + connectorUrl,
                                "--inpay.journey.authorization=" + auth,
                                "--inpay.journey.sandbox-fixtures=" + LIVE,
                                "--inpay.journey.mode="
                                        + (LIVE ? "cielo-sandbox" : "local-simulator"));
        base =
                "http://127.0.0.1:"
                        + ((WebServerApplicationContext) context).getWebServer().getPort();
        var cookies = new CookieManager();
        cookies.setCookiePolicy(CookiePolicy.ACCEPT_ALL);
        client = HttpClient.newBuilder().cookieHandler(cookies).build();
        if (System.getenv("PHP_BIN") != null) {
            Path root = Path.of(System.getProperty("user.dir")).toAbsolutePath();
            while (!Files.isDirectory(root.resolve("examples/game-store"))) root = root.getParent();
            int port;
            try (var socket = new java.net.ServerSocket(0)) {
                port = socket.getLocalPort();
            }
            var command =
                    new ProcessBuilder(
                            System.getenv("PHP_BIN"),
                            "-S",
                            "127.0.0.1:" + port,
                            "-t",
                            root.resolve("examples/game-store/public").toString());
            command.environment().put("INPAY_URL", base);
            command.environment().put("STORE_MODE", LIVE ? "cielo-sandbox" : "local-simulator");
            command.environment().put("INPAY_AUTHORIZATION", auth);
            command.environment().put("STORE_ADMIN_PASSWORD", "test-admin-only");
            command.environment()
                    .put(
                            "STORE_DATA",
                            Files.createTempDirectory("inpay-store-")
                                    .resolve("store.json")
                                    .toString());
            command.redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD);
            shop = command.start();
            store = "http://127.0.0.1:" + port;
            for (int i = 0; i < 50; i++) {
                try {
                    get("/");
                    break;
                } catch (Exception unavailable) {
                    Thread.sleep(100);
                }
            }
        }
    }

    @AfterAll
    static void stop() throws Exception {
        if (shop != null) shop.destroy();
        if (cielo != null) {
            cielo.destroy();
            cielo.waitFor(10, TimeUnit.SECONDS);
        }
        if (context != null) context.close();
        if (pg != null) pg.close();
        if (simulator != null) simulator.stop(0);
    }

    static String get(String path) throws Exception {
        return client.send(
                        HttpRequest.newBuilder(URI.create(store + path)).GET().build(),
                        HttpResponse.BodyHandlers.ofString())
                .body();
    }

    static String field(String html, String name) {
        var matcher = Pattern.compile("name=\"" + name + "\" value=\"([^\"]+)\"").matcher(html);
        if (!matcher.find()) throw new AssertionError("Missing form field " + name);
        return matcher.group(1);
    }

    static int post(Map<String, String> values) throws Exception {
        String body =
                values.entrySet().stream()
                        .map(
                                e ->
                                        URLEncoder.encode(
                                                        e.getKey(),
                                                        java.nio.charset.StandardCharsets.UTF_8)
                                                + "="
                                                + URLEncoder.encode(
                                                        e.getValue(),
                                                        java.nio.charset.StandardCharsets.UTF_8))
                        .collect(java.util.stream.Collectors.joining("&"));
        return client.send(
                        HttpRequest.newBuilder(URI.create(store + "/"))
                                .header("Content-Type", "application/x-www-form-urlencoded")
                                .POST(HttpRequest.BodyPublishers.ofString(body))
                                .build(),
                        HttpResponse.BodyHandlers.ofString())
                .statusCode();
    }

    @Test
    void shopCheckoutRefreshAndCancellationForFiveMethods() throws Exception {
        Assumptions.assumeTrue(shop != null, "Set PHP_BIN to run the real PHP storefront E2E");
        assertEquals(
                10, Pattern.compile("class=\"product-body\"").matcher(get("/")).results().count());
        for (String method :
                LIVE
                        ? List.of("credit_card")
                        : List.of("credit_card", "debit_card", "pix", "boleto", "transfer")) {
            String csrf = field(get("/"), "csrf");
            assertEquals(303, post(Map.of("csrf", csrf, "action", "add", "product", "1")));
            String checkout = get("/?page=checkout");
            String key = field(checkout, "checkout_key");
            assertEquals(
                    303,
                    post(
                            Map.of(
                                    "csrf",
                                    csrf,
                                    "action",
                                    "checkout",
                                    "checkout_key",
                                    key,
                                    "method",
                                    method)));
            assertTrue(get("/?page=orders").contains(method));
            assertEquals(303, post(Map.of("csrf", csrf, "action", "refresh", "order", key)));
            assertTrue(get("/?page=orders").contains("PAID"));
            assertEquals(303, post(Map.of("csrf", csrf, "action", "cancel", "order", key)));
            assertTrue(get("/?page=orders").contains("CANCELLED"));
        }
        assertEquals(
                303,
                post(
                        Map.of(
                                "csrf",
                                field(get("/"), "csrf"),
                                "action",
                                "login",
                                "password",
                                "test-admin-only")));
        assertTrue(get("/?page=admin").contains("Gerenciar catálogo"));
    }

    @Test
    void replayDoesNotCreateSecondProviderPayment() throws Exception {
        Assumptions.assumeFalse(LIVE, "Provider call counter applies to local simulator only");
        String key = UUID.randomUUID().toString();
        String body =
                json.writeValueAsString(
                        Map.of(
                                "merchantOrder",
                                key,
                                "amount",
                                1000,
                                "method",
                                "pix",
                                "instrument",
                                "fixture-approved"));
        int before = payments.size();
        var request =
                HttpRequest.newBuilder(URI.create(base + "/gateway/v1/journeys"))
                        .header("Authorization", auth)
                        .header("Content-Type", "application/json")
                        .header("Idempotency-Key", key)
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build();
        var one = client.send(request, HttpResponse.BodyHandlers.ofString());
        var two = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, one.statusCode());
        assertEquals(json.readTree(one.body()).path("id"), json.readTree(two.body()).path("id"));
        assertEquals(before + 1, payments.size());
        var conflict =
                HttpRequest.newBuilder(request.uri())
                        .header("Authorization", auth)
                        .header("Content-Type", "application/json")
                        .header("Idempotency-Key", key)
                        .POST(HttpRequest.BodyPublishers.ofString(body.replace("1000", "2000")))
                        .build();
        assertEquals(
                409, client.send(conflict, HttpResponse.BodyHandlers.discarding()).statusCode());
        assertEquals(before + 1, payments.size());
        String id = json.readTree(one.body()).path("id").asText();
        var foreign =
                HttpRequest.newBuilder(URI.create(base + "/gateway/v1/journeys/" + id))
                        .header("Authorization", auth)
                        .header("X-Merchant-Id", UUID.randomUUID().toString())
                        .GET()
                        .build();
        assertEquals(
                403, client.send(foreign, HttpResponse.BodyHandlers.discarding()).statusCode());
        var anonymous = HttpRequest.newBuilder(foreign.uri()).GET().build();
        assertEquals(
                401, client.send(anonymous, HttpResponse.BodyHandlers.discarding()).statusCode());
    }

    /** Inicia outro processo Java; o Gateway não depende do artefato conector no classpath. */
    static String launchCielo() throws Exception {
        assertEquals(
                "https://apisandbox.cieloecommerce.cielo.com.br", System.getenv("CIELO_BASE_URL"));
        assertEquals(
                "https://apiquerysandbox.cieloecommerce.cielo.com.br",
                System.getenv("CIELO_QUERY_URL"));
        try (var connection = pg.getPostgresDatabase().getConnection();
                var statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE cielo_store");
        }
        Path root = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (!Files.isDirectory(root.resolve("examples/game-store"))) root = root.getParent();
        int port;
        try (var socket = new java.net.ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        List<String> command = new ArrayList<>();
        command.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        for (String property :
                List.of(
                        "https.proxyHost",
                        "https.proxyPort",
                        "http.nonProxyHosts",
                        "javax.net.ssl.trustStore"))
            if (System.getProperty(property) != null)
                command.add("-D" + property + "=" + System.getProperty(property));
        command.addAll(
                List.of(
                        "-jar",
                        root.resolve(
                                        "services/connector-cielo/target/connector-cielo-0.2.0-SNAPSHOT.jar")
                                .toString(),
                        "--spring.profiles.active=local",
                        "--server.port=" + port,
                        "--spring.datasource.url=jdbc:postgresql://localhost:"
                                + pg.getPort()
                                + "/cielo_store",
                        "--spring.datasource.username=postgres",
                        "--spring.datasource.password=",
                        "--inpay.local-password=integration-only",
                        "--inpay.provider.enabled=true",
                        "--inpay.provider.allow-sensitive-card-data=true",
                        "--inpay.provider.tenant-id=11111111-1111-1111-1111-111111111111",
                        "--spring.kafka.listener.auto-startup=false",
                        "--inpay.outbox.delay=3600000",
                        "--logging.level.root=WARN"));
        cielo =
                new ProcessBuilder(command)
                        .redirectErrorStream(true)
                        .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                        .start();
        String url = "http://127.0.0.1:" + port;
        var http = HttpClient.newHttpClient();
        for (int i = 0; i < 200; i++) {
            if (!cielo.isAlive()) throw new IllegalStateException("Cielo connector startup failed");
            try {
                if (http.send(
                                        HttpRequest.newBuilder(URI.create(url + "/actuator/health"))
                                                .GET()
                                                .build(),
                                        HttpResponse.BodyHandlers.discarding())
                                .statusCode()
                        > 0) return url;
            } catch (Exception unavailable) {
                Thread.sleep(100);
            }
        }
        throw new IllegalStateException("Cielo connector startup timeout");
    }

    /** Mantém o laboratório disponível para inspeção local, com fixtures e dados temporários. */
    public static void main(String[] args) throws Exception {
        start();
        System.out.println("INPAY_LOCAL_STORE=" + store);
        System.out.println("INPAY_LOCAL_GATEWAY=" + base);
        Runtime.getRuntime()
                .addShutdownHook(
                        new Thread(
                                () -> {
                                    try {
                                        stop();
                                    } catch (Exception ignored) {
                                    }
                                }));
        new CountDownLatch(1).await();
    }
}
