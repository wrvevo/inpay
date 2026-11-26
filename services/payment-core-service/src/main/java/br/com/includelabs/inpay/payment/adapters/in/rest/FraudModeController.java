package br.com.includelabs.inpay.payment.adapters.in.rest;

import br.com.includelabs.inpay.contracts.FraudMode;
import br.com.includelabs.inpay.payment.bootstrap.FraudModeHolder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Escolhe o antifraude nativo do gateway e replica a escolha no fraud-service. */
@RestController
@RequestMapping("/gateway/v1/fraud-mode")
@Tag(name = "Antifraude")
public class FraudModeController {
    private final FraudModeHolder holder;
    private final String fraudUrl;
    private final String password;
    private final HttpClient http =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();

    /** Inicializa as dependências do controle de modo antifraude. */
    public FraudModeController(
            FraudModeHolder holder,
            @Value("${inpay.fraud.url:http://127.0.0.1:8087}") String fraudUrl,
            @Value("${inpay.local-password:}") String password) {
        this.holder = holder;
        this.fraudUrl = fraudUrl;
        this.password = password;
    }

    /** Consulta o modo antifraude ativo no Gateway. */
    @GetMapping
    @PreAuthorize("hasAuthority('SCOPE_payments/read')")
    @Operation(
            summary = "Consultar o antifraude nativo",
            description =
                    "INPAY_RISK usa o motor local medido em tokens. CLEARSALE e SERASA_EXPERIAN"
                            + " usam os conectores em modo simulado.")
    public Map<String, Object> current() {
        return body(holder.current(), false);
    }

    /** Altera o modo e informa se a sincronização com o serviço de risco ocorreu. */
    @PutMapping
    @PreAuthorize("hasAuthority('SCOPE_payments/create')")
    @Operation(
            summary = "Selecionar o antifraude nativo",
            description = "A escolha vale para este processo e é enviada ao fraud-service.")
    public Map<String, Object> select(@RequestBody Map<String, String> request) {
        String mode = holder.select(request.get("mode"));
        return body(mode, sync(mode));
    }

    /** Monta o contrato de resposta sem expor credenciais. */
    private Map<String, Object> body(String mode, boolean synced) {
        return Map.of("mode", mode, "providers", FraudMode.all(), "syncedWithFraudService", synced);
    }

    /** Replica o modo selecionado no serviço de risco e sinaliza indisponibilidade. */
    private boolean sync(String mode) {
        if (password.isBlank() || fraudUrl.isBlank()) {
            return false;
        }
        try {
            String token =
                    Base64.getEncoder()
                            .encodeToString(("inpay:" + password).getBytes(StandardCharsets.UTF_8));
            HttpRequest request =
                    HttpRequest.newBuilder(URI.create(fraudUrl + "/internal/v1/fraud-mode"))
                            .timeout(Duration.ofSeconds(2))
                            .header("Authorization", "Basic " + token)
                            .header("Content-Type", "application/json")
                            .POST(
                                    HttpRequest.BodyPublishers.ofString(
                                            "{\"mode\":\"" + mode + "\"}"))
                            .build();
            HttpResponse<Void> response =
                    http.send(request, HttpResponse.BodyHandlers.discarding());
            return response.statusCode() >= 200 && response.statusCode() < 300;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception exception) {
            return false;
        }
    }
}
