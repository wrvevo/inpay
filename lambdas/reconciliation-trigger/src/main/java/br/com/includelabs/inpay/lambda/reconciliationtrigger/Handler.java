package br.com.includelabs.inpay.lambda.reconciliationtrigger;

import br.com.includelabs.inpay.security.LambdaRuntimeSecret;
import br.com.includelabs.inpay.security.M2mTokenClient;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.function.Function;

/**
 * Dispara um job interno; a identidade e o endereço vêm do deployment, nunca do evento recebido.
 */
public class Handler implements RequestHandler<Map<String, Object>, Map<String, Object>> {
    private final boolean localRuntime;
    private final HttpClient httpClient;
    private final M2mTokenClient injectedTokenClient;

    /**
     * Configura o modo AWS por padrão. O Compose ativa explicitamente o modo local com HTTP Basic.
     *
     * @throws IllegalStateException se faltar uma configuração obrigatória para o modo selecionado
     */
    public Handler() {
        localRuntime = "local".equals(System.getenv("INPAY_RUNTIME"));
        httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        String secretId = System.getenv("INPAY_RUNTIME_SECRET_ID");
        injectedTokenClient =
                localRuntime || (secretId != null && !secretId.isBlank())
                        ? null
                        : new M2mTokenClient(
                                URI.create(required("TOKEN_ENDPOINT")),
                                required("CLIENT_ID"),
                                required("CLIENT_SECRET"));
    }

    /**
     * Envia um comando vazio ao endpoint fixo do job e só anuncia aceitação após resposta 2xx.
     *
     * @param event evento do EventBridge ou da emulação local; não fornece URL nem credenciais
     * @param context contexto de execução da Lambda
     * @return status accepted quando o serviço confirmar o recebimento
     * @throws IllegalStateException se a configuração, autenticação ou resposta do destino impedir
     *     o job
     */
    @Override
    public Map<String, Object> handleRequest(Map<String, Object> event, Context context) {
        Function<String, String> settings =
                LambdaRuntimeSecret.settings("reconciliation-trigger", localRuntime, httpClient);
        URI endpoint = URI.create(settings.apply("TARGET_ENDPOINT"));
        if (!localRuntime && !"https".equals(endpoint.getScheme())) {
            throw new IllegalStateException("HTTPS required outside local runtime");
        }
        try {
            HttpRequest request =
                    HttpRequest.newBuilder(endpoint)
                            .timeout(Duration.ofSeconds(10))
                            .header("Authorization", authorizationHeader(settings))
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString("{}"))
                            .build();
            HttpResponse<Void> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Job not accepted; HTTP " + response.statusCode());
            }
            return Map.of("status", "accepted");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Job interrupted", exception);
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("Target unavailable", exception);
        }
    }

    /**
     * Seleciona a credencial conforme o runtime, sem realizar fallback após falha de Cognito.
     *
     * @return cabeçalho Authorization destinado exclusivamente ao backend configurado
     */
    private String authorizationHeader(Function<String, String> settings) {
        if (localRuntime) {
            String credentials = "inpay:" + required("LOCAL_PASSWORD");
            return "Basic "
                    + Base64.getEncoder()
                            .encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
        }
        M2mTokenClient tokenClient = injectedTokenClient;
        if (tokenClient == null) {
            tokenClient =
                    new M2mTokenClient(
                            URI.create(settings.apply("TOKEN_ENDPOINT")),
                            settings.apply("CLIENT_ID"),
                            settings.apply("CLIENT_SECRET"));
        }
        return "Bearer " + tokenClient.token(settings.apply("TARGET_SCOPE"));
    }

    /**
     * Lê um valor obrigatório sem registrá-lo em logs.
     *
     * @param name nome da variável de ambiente
     * @return conteúdo da variável configurada
     * @throws IllegalStateException se a variável estiver ausente ou vazia
     */
    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing " + name);
        }
        return value;
    }
}
