package br.com.includelabs.inpay.cielo.integration;

import static org.junit.jupiter.api.Assertions.*;

import br.com.includelabs.inpay.cielo.bootstrap.Application;
import br.com.includelabs.inpay.connector.JsonFields;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import tools.jackson.databind.ObjectMapper;

/** Integração opt-in: API HTTP InPay real, PostgreSQL real e somente sandbox oficial Cielo. */
class CieloSandboxIT {
    static final ObjectMapper JSON = new ObjectMapper();
    static final List<Map<String, Object>> REPORT = new ArrayList<>();
    static final HttpClient LOCAL =
            HttpClient.newBuilder()
                    .proxy(
                            new java.net.ProxySelector() {
                                public List<java.net.Proxy> select(URI uri) {
                                    return List.of(java.net.Proxy.NO_PROXY);
                                }

                                public void connectFailed(
                                        URI uri,
                                        java.net.SocketAddress address,
                                        java.io.IOException exception) {}
                            })
                    .build();
    static EmbeddedPostgres postgres;
    static ConfigurableApplicationContext context;
    static String base;
    static String paymentId;
    static String scenario = "credit-lifecycle";

    @BeforeAll
    static void start() throws Exception {
        assertEquals(
                "true", System.getenv("CIELO_SANDBOX_TEST"), "Explicit sandbox opt-in required");
        assertEquals(
                "https://apisandbox.cieloecommerce.cielo.com.br", System.getenv("CIELO_BASE_URL"));
        assertEquals(
                "https://apiquerysandbox.cieloecommerce.cielo.com.br",
                System.getenv("CIELO_QUERY_URL"));
        assertNotNull(
                System.getenv("CIELO_MERCHANT_KEY"), "Configure sandbox credentials outside git");
        postgres =
                EmbeddedPostgres.builder()
                        .setPort(0)
                        .setServerConfig("unix_socket_directories", "")
                        .start();
        context =
                new SpringApplicationBuilder(Application.class)
                        .run(
                                "--spring.profiles.active=local",
                                "--server.port=0",
                                "--spring.datasource.url=jdbc:postgresql://localhost:"
                                        + postgres.getPort()
                                        + "/postgres",
                                "--spring.datasource.username=postgres",
                                "--spring.datasource.password=",
                                "--inpay.local-password=sandbox-integration-test-only",
                                "--spring.kafka.listener.auto-startup=false",
                                "--inpay.outbox.delay=3600000",
                                "--logging.level.root=WARN",
                                "--spring.main.banner-mode=off");
        base =
                "http://127.0.0.1:"
                        + ((WebServerApplicationContext) context).getWebServer().getPort();
    }

    @AfterAll
    static void stop() throws Exception {
        Path report = Path.of("target/cielo-sandbox-report.json");
        Files.createDirectories(report.getParent());
        Files.writeString(
                report,
                JSON.writerWithDefaultPrettyPrinter()
                        .writeValueAsString(
                                Map.of(
                                        "executedAt",
                                        Instant.now().toString(),
                                        "environment",
                                        "Cielo sandbox",
                                        "results",
                                        REPORT)));
        if (context != null) {
            context.close();
        }
        if (postgres != null) {
            postgres.close();
        }
    }

    @Test
    void completeSandboxLifecycleThroughInPay() throws Exception {
        Map<String, Object> sale = sale();
        UUID key = UUID.randomUUID();
        Map<String, Object> first =
                call(
                        "POST",
                        "/internal/v1/provider-payments",
                        Map.of("operationId", key.toString(), "payload", sale));
        record("LEGACY_AUTHORIZE", first);
        assertEquals(
                "1",
                text(first, "status"),
                "Sandbox authorization must succeed before dependent operations");
        assertEquals(false, first.get("uncertain"));
        paymentId = text(first, "providerId");
        assertFalse(paymentId.isBlank());
        assertEquals(
                first,
                call(
                        "POST",
                        "/internal/v1/provider-payments",
                        Map.of("operationId", key.toString(), "payload", sale)),
                "Same key must replay the durable result");
        Map<String, Object> query = read("GET_PAYMENT", paymentId);
        assertEquals("1", text(query, "data.Payment.Status"));
        Map<String, Object> capture = command("CAPTURE_PAYMENT", paymentId, null, null);
        assertEquals("2", text(capture, "data.Status"));
        Map<String, Object> cancel = command("VOID_PAYMENT", paymentId, null, null);
        assertEquals("10", text(cancel, "data.Status"));
        read("GET_PAYMENT", paymentId);

        scenario = "tokenization";
        Map<String, Object> rawCard = card();
        rawCard.put("CustomerName", "Cliente Sandbox InPay");
        rawCard.remove("SecurityCode");
        Map<String, Object> created = command("CREATE_CARD", null, null, rawCard);
        String cardToken = text(created, "data.CardToken");

        if (!cardToken.isBlank()) {
            read("GET_CARD", cardToken);
            command(
                    "ZERO_AUTH",
                    null,
                    null,
                    Map.of("CardToken", cardToken, "Brand", "Visa", "SaveCard", false));
            Map<String, Object> tokenSale = sale();
            payment(tokenSale).put("CreditCard", Map.of("CardToken", cardToken, "Brand", "Visa"));
            Map<String, Object> authorized = command("CREATE_SALE", null, null, tokenSale);
            String tokenPayment = text(authorized, "data.Payment.PaymentId");
            if (!tokenPayment.isBlank()) {
                command("VOID_PAYMENT", tokenPayment, null, null);
            }
        }
        if (cardToken.isBlank()) {
            REPORT.add(
                    Map.of(
                            "scenario",
                            scenario,
                            "outcome",
                            "BLOCKED",
                            "reason",
                            "CardToken absent; GET_CARD and token payment not executed"));
        }
        scenario = "zero-auth";
        Map<String, Object> zero = card();
        zero.put("CardType", "CreditCard");
        zero.put("SaveCard", false);
        command("ZERO_AUTH", null, null, zero);
        scenario = "bin-query";
        read("QUERY_BIN", "411111");

        scenario = "partial-capture-void";
        Map<String, Object> partial = command("CREATE_SALE", null, null, sale());
        String partialId = text(partial, "data.Payment.PaymentId");
        if (!partialId.isBlank()) {
            command("CAPTURE_PAYMENT", partialId, 400L, null);
            command("VOID_PAYMENT", partialId, 400L, null);
        }
        scenario = "void-by-order";
        Map<String, Object> byOrder = sale();
        command("CREATE_SALE", null, null, byOrder);
        command("VOID_ORDER", text(byOrder, "MerchantOrderId"), null, null);
        scenario = "declined-credit";
        Map<String, Object> declined = sale();
        payment(declined)
                .put(
                        "CreditCard",
                        Map.of(
                                "CardNumber",
                                "4024007153763192",
                                "Holder",
                                "TESTE",
                                "ExpirationDate",
                                "12/2030",
                                "SecurityCode",
                                "123",
                                "Brand",
                                "Visa"));
        assertEquals(
                "3", text(command("CREATE_SALE", null, null, declined), "data.Payment.Status"));
        recurrent();
        otherProducts();
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> payment(Map<String, Object> sale) {
        return (Map<String, Object>) sale.get("Payment");
    }

    static Map<String, Object> card() {
        return new LinkedHashMap<>(
                Map.of(
                        "CardNumber",
                        "4111111111111111",
                        "Holder",
                        "CLIENTE TESTE",
                        "ExpirationDate",
                        "12/2030",
                        "SecurityCode",
                        "123",
                        "Brand",
                        "Visa"));
    }

    static Map<String, Object> sale() {
        return new LinkedHashMap<>(
                Map.of(
                        "MerchantOrderId",
                        "inpay-" + UUID.randomUUID(),
                        "Customer",
                        Map.of("Name", "Cliente Sandbox InPay"),
                        "Payment",
                        new LinkedHashMap<>(
                                Map.of(
                                        "Type",
                                        "CreditCard",
                                        "Amount",
                                        1000,
                                        "Installments",
                                        1,
                                        "Capture",
                                        false,
                                        "CreditCard",
                                        card()))));
    }

    static void recurrent() throws Exception {
        scenario = "recurrence";
        Map<String, Object> sale = sale();
        payment(sale)
                .put(
                        "RecurrentPayment",
                        Map.of(
                                "AuthorizeNow",
                                true,
                                "EndDate",
                                LocalDate.now().plusMonths(6).toString(),
                                "Interval",
                                "Monthly"));
        Map<String, Object> result = command("CREATE_SALE", null, null, sale);
        String id = text(result, "data.Payment.RecurrentPayment.RecurrentPaymentId");
        if (id.isBlank()) {
            REPORT.add(
                    Map.of(
                            "scenario",
                            scenario,
                            "outcome",
                            "BLOCKED",
                            "reason",
                            "RecurrentPaymentId absent; recurrence queries and updates not executed"));
            String initialPayment = text(result, "data.Payment.PaymentId");
            if (!initialPayment.isBlank()) {
                command("VOID_PAYMENT", initialPayment, null, null);
            }
            return;
        }
        read("GET_RECURRENT", id);
        command(
                "UPDATE_RECURRENT_CUSTOMER",
                id,
                null,
                Map.of("Name", "Cliente Sandbox Atualizado"));
        command("UPDATE_RECURRENT_INTERVAL", id, null, 1);
        command("UPDATE_RECURRENT_END_DATE", id, null, LocalDate.now().plusMonths(3).toString());
        command("UPDATE_RECURRENT_DAY", id, null, 20);
        command("UPDATE_RECURRENT_INSTALLMENTS", id, null, 1);
        command("UPDATE_RECURRENT_AMOUNT", id, null, 1200);
        command("UPDATE_RECURRENT_NEXT_DATE", id, null, LocalDate.now().plusMonths(1).toString());
        command(
                "UPDATE_RECURRENT_PAYMENT",
                id,
                null,
                Map.of(
                        "Type",
                        "CreditCard",
                        "Amount",
                        1200,
                        "Installments",
                        1,
                        "CreditCard",
                        card()));
        command("DEACTIVATE_RECURRENT", id, null, null);
        command("REACTIVATE_RECURRENT", id, null, null);
        command("DEACTIVATE_RECURRENT", id, null, null);
        String payment = text(result, "data.Payment.PaymentId");
        if (!payment.isBlank()) {
            command("VOID_PAYMENT", payment, null, null);
        }
    }

    static void otherProducts() throws Exception {
        scenario = "debit";
        Map<String, Object> debit = sale();
        var body = payment(debit);
        body.put("Type", "DebitCard");
        body.put("DebitCard", body.remove("CreditCard"));
        body.remove("Installments");
        body.put("ReturnUrl", "https://example.com/return");
        command("CREATE_SALE", null, null, debit);
        scenario = "boleto";
        Map<String, Object> boleto = sale();
        boleto.put(
                "Payment",
                Map.of(
                        "Type",
                        "Boleto",
                        "Amount",
                        1000,
                        "Provider",
                        "Simulado",
                        "ExpirationDate",
                        LocalDate.now().plusDays(3).toString(),
                        "Instructions",
                        "Teste sandbox"));
        command("CREATE_SALE", null, null, boleto);
        scenario = "electronic-transfer";
        Map<String, Object> transfer = sale();
        transfer.put(
                "Payment",
                Map.of(
                        "Type",
                        "ElectronicTransfer",
                        "Amount",
                        1000,
                        "Provider",
                        "Simulado",
                        "ReturnUrl",
                        "https://example.com/return"));
        command("CREATE_SALE", null, null, transfer);
    }

    static Map<String, Object> command(String operation, String id, Long amount, Object payload)
            throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("operationId", UUID.randomUUID().toString());
        if (id != null) {
            body.put("resourceId", id);
        }
        if (amount != null) {
            body.put("amount", amount);
        }
        if (payload != null) {
            body.put("payload", payload);
        }
        Map<String, Object> result =
                call("POST", "/internal/v1/cielo/operations/" + operation, body);
        record(operation, result);
        return result;
    }

    static Map<String, Object> read(String operation, String id) throws Exception {
        Map<String, Object> result =
                call(
                        "GET",
                        "/internal/v1/cielo/operations/" + operation + "?resourceId=" + id,
                        null);
        record(operation, result);
        return result;
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> call(String method, String path, Object body) throws Exception {
        var builder =
                HttpRequest.newBuilder(URI.create(base + path))
                        .timeout(Duration.ofSeconds(45))
                        .header(
                                "Authorization",
                                "Basic "
                                        + Base64.getEncoder()
                                                .encodeToString(
                                                        "developer:sandbox-integration-test-only"
                                                                .getBytes(StandardCharsets.UTF_8)))
                        .header("Content-Type", "application/json");
        builder.method(
                method,
                body == null
                        ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)));
        var reply = LOCAL.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        Map<String, Object> result = JSON.readValue(reply.body(), Map.class);
        if (reply.statusCode() != 200) {
            return Map.of(
                    "inpayHttpStatus",
                    reply.statusCode(),
                    "errorCode",
                    Objects.toString(result.get("code"), ""));
        }
        return result;
    }

    static String text(Object body, String path) {
        return JsonFields.text(body, path);
    }

    static void record(String operation, Map<String, Object> result) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("operation", operation);
        row.put("scenario", scenario);
        for (String key :
                List.of(
                        "httpStatus",
                        "inpayHttpStatus",
                        "uncertain",
                        "replayed",
                        "status",
                        "errorCode")) {
            if (result.containsKey(key)) {
                row.put(key, result.get(key));
            }
        }
        for (String key :
                List.of(
                        "Payment.Status",
                        "Status",
                        "Payment.ReturnCode",
                        "ReturnCode",
                        "Code",
                        "Valid",
                        "items.0.Code")) {
            String value = text(result, "data." + key);
            if (!value.isBlank()) {
                row.put(key, value);
            }
        }
        if (paymentId != null) {
            row.put("primaryLifecyclePaymentId", paymentId);
        }
        REPORT.add(row);
    }
}
