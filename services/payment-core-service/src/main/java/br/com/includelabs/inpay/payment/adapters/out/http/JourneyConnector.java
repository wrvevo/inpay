package br.com.includelabs.inpay.payment.adapters.out.http;

import br.com.includelabs.inpay.payment.domain.JourneyPort;
import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** Cliente REST do conector. Credenciais Cielo permanecem exclusivamente na aplicação conectora. */
@Component
public class JourneyConnector implements JourneyPort {
    private final String url, authorization, mode;
    private final boolean sandboxFixtures;
    private final JsonMapper json;
    private final HttpClient http =
            HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .followRedirects(HttpClient.Redirect.NEVER)
                    .build();

    public JourneyConnector(
            @Value("${inpay.journey.connector-url:}") String url,
            @Value("${inpay.journey.authorization:}") String authorization,
            @Value("${inpay.journey.mode:disabled}") String mode,
            @Value("${inpay.journey.sandbox-fixtures:false}") boolean sandboxFixtures,
            @Value("${spring.profiles.active:}") String profiles,
            JsonMapper json) {
        this.url = url;
        this.authorization = authorization;
        this.mode = mode;
        this.sandboxFixtures =
                sandboxFixtures && Arrays.asList(profiles.split(",")).contains("local");
        this.json = json;
    }

    /** Transforma contrato normalizado em venda Cielo; fixtures só no modo explicitamente local. */
    public Result create(UUID tenant, Request request) {
        Map<String, Object> payment = new LinkedHashMap<>();
        payment.put("Amount", request.amount());
        switch (request.method()) {
            case "pix" -> {
                payment.put("Type", "Pix");
                payment.put("Provider", "Cielo2");
                payment.put("QrCode", Map.of("Expiration", 600));
            }
            case "credit_card", "debit_card" -> {
                boolean debit = request.method().equals("debit_card");
                payment.put("Type", debit ? "DebitCard" : "CreditCard");
                payment.put("Installments", 1);
                payment.put("Capture", true);
                Map<String, Object> card;
                if (mode.equals("local-simulator")
                        && "fixture-approved".equals(request.instrument()))
                    card =
                            Map.of(
                                    "CardToken",
                                    "11111111-1111-1111-1111-111111111111",
                                    "Brand",
                                    "Visa");
                else if (mode.equals("cielo-sandbox")
                        && sandboxFixtures
                        && "sandbox-approved".equals(request.instrument())) {
                    card =
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
                                    "Visa");
                } else {
                    UUID.fromString(request.instrument());
                    card = Map.of("CardToken", request.instrument(), "Brand", "Visa");
                }
                payment.put(debit ? "DebitCard" : "CreditCard", card);
                if (debit) payment.put("ReturnUrl", "http://localhost:8099/return");
            }
            case "boleto" -> {
                payment.put("Type", "Boleto");
                payment.put("Provider", "Simulado");
                payment.put("BoletoNumber", request.merchantOrder());
                payment.put("ExpirationDate", java.time.LocalDate.now().plusDays(3).toString());
            }
            case "transfer" -> {
                payment.put("Type", "ElectronicTransfer");
                payment.put("Provider", "Simulado");
                payment.put("ReturnUrl", "http://localhost:8099/return");
            }
            default -> throw new IllegalArgumentException();
        }
        // Simulado is a sandbox contract, never a production bank selection.
        if (!mode.equals("local-simulator")
                && !Set.of("credit_card", "pix").contains(request.method()))
            throw new IllegalStateException("Method requires homologated provider configuration");
        return execute(
                tenant,
                "CREATE_SALE",
                request.operationId(),
                null,
                request.amount(),
                Map.of(
                        "MerchantOrderId",
                        request.merchantOrder(),
                        "Customer",
                        Map.of("Name", "InPay integration customer"),
                        "Payment",
                        payment),
                false);
    }

    public Result query(UUID tenant, String id, long amount) {
        return execute(tenant, "GET_PAYMENT", null, id, amount, null, true);
    }

    public Result cancel(UUID tenant, UUID operation, String id, long amount) {
        return execute(tenant, "VOID_PAYMENT", operation, id, amount, null, false);
    }

    private Result execute(
            UUID tenant,
            String operation,
            UUID operationId,
            String id,
            long amount,
            Object payload,
            boolean read) {
        if (!Set.of("local-simulator", "cielo-sandbox").contains(mode)
                || url.isBlank()
                || authorization.isBlank())
            throw new IllegalStateException("Journey connector disabled");
        URI base = URI.create(url);
        if (!Set.of("http", "https").contains(base.getScheme()) || base.getUserInfo() != null)
            throw new IllegalStateException();
        try {
            String target =
                    url.replaceAll("/+$", "")
                            + "/internal/v1/cielo/operations/"
                            + operation
                            + (read
                                    ? "?resourceId="
                                            + URLEncoder.encode(
                                                    id, java.nio.charset.StandardCharsets.UTF_8)
                                    : "");
            var builder =
                    HttpRequest.newBuilder(URI.create(target))
                            .timeout(Duration.ofSeconds(20))
                            .header("Authorization", authorization)
                            .header("X-Merchant-Id", tenant.toString())
                            .header("Content-Type", "application/json");
            if (read) builder.GET();
            else {
                var body = new LinkedHashMap<String, Object>();
                body.put("operationId", operationId);
                body.put("resourceId", id);
                if (operation.equals("VOID_PAYMENT")) body.put("amount", amount);
                body.put("payload", payload);
                builder.POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
            }
            var reply = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (reply.statusCode() != 200)
                return new Result(
                        "UNKNOWN",
                        Objects.toString(id, ""),
                        Map.of("diagnostic", "CONNECTOR_HTTP_" + reply.statusCode()));
            var envelope = json.readTree(reply.body());
            if (envelope.path("uncertain").asBoolean()
                    || envelope.path("httpStatus").asInt() >= 400)
                return new Result(
                        "UNKNOWN",
                        Objects.toString(id, ""),
                        Map.of(
                                "diagnostic",
                                "PROVIDER_HTTP_" + envelope.path("httpStatus").asInt()));
            var data = envelope.path("data");
            var p = data.has("Payment") ? data.path("Payment") : data;
            if (p.has("Amount") && p.path("Amount").asLong() != amount)
                return new Result(
                        "UNKNOWN",
                        Objects.toString(id, ""),
                        Map.of("diagnostic", "AMOUNT_MISMATCH"));
            int nativeStatus = p.path("Status").asInt(-1);
            String status =
                    switch (nativeStatus) {
                        case 1 -> "AUTHORIZED";
                        case 2 -> "PAID";
                        case 3 -> "DECLINED";
                        case 10, 11 -> "CANCELLED";
                        case 0, 12, 20 -> "PENDING";
                        default -> "UNKNOWN";
                    };
            Map<String, String> artifacts = new LinkedHashMap<>();
            for (String field :
                    List.of(
                            "QrCodeString",
                            "BarCodeNumber",
                            "DigitableLine",
                            "Url",
                            "AuthenticationUrl")) {
                if (p.has(field)) artifacts.put(field, p.path(field).asText());
            }
            return new Result(
                    status, p.path("PaymentId").asText(Objects.toString(id, "")), artifacts);
        } catch (Exception e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            return new Result(
                    "UNKNOWN",
                    Objects.toString(id, ""),
                    Map.of("diagnostic", "CONNECTOR_UNAVAILABLE"));
        }
    }
}
