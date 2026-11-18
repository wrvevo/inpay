package br.com.includelabs.inpay.security;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import tools.jackson.databind.json.JsonMapper;

/** Explicit per-destination client. Secrets injected from Secrets Manager by the deployment. */
public final class M2mTokenClient {

    private final URI endpoint;

    private final String clientId;

    private final String secret;

    private final Map<String, Cached> cache = new ConcurrentHashMap<>();

    private final HttpClient httpClient =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();

    private final JsonMapper mapper = JsonMapper.builder().build();

    /**
     * Componente de infraestrutura do InPay responsável por Cached. Não deve ser referenciado pelo
     * domínio.
     */
    private record Cached(String token, Instant expiresAt) {}

    /**
     * Inicializa M2mTokenClient com suas dependências explícitas.
     *
     * @param endpoint endereço HTTPS controlado pelo deployment, nunca fornecido pelo usuário final
     * @param clientId identificador da aplicação confidencial no Cognito
     * @param secret segredo obtido por configuração segura; não registrar em logs
     * @throws IllegalArgumentException quando a validação ou a capacidade exigida impedir a
     *     conclusão da operação
     */
    public M2mTokenClient(URI endpoint, String clientId, String secret) {
        if (!"https".equals(endpoint.getScheme())) {
            throw new IllegalArgumentException("HTTPS required");
        }
        this.endpoint = endpoint;
        this.clientId = clientId;
        this.secret = secret;
    }

    /**
     * Obtém um access token por client_credentials e reutiliza o cache por escopos até 30 segundos
     * antes da expiração.
     *
     * @param scopes escopos mínimos requeridos pelo destino
     * @return access token válido para os escopos solicitados
     * @throws IllegalStateException quando a validação ou a capacidade exigida impedir a conclusão
     *     da operação
     */
    public synchronized String token(String scopes) {
        Cached cachedToken = cache.get(scopes);
        if (cachedToken != null && cachedToken.expiresAt().isAfter(Instant.now().plusSeconds(30))) {
            return cachedToken.token();
        }
        String credentials =
                Base64.getEncoder()
                        .encodeToString((clientId + ":" + secret).getBytes(StandardCharsets.UTF_8));
        var request =
                HttpRequest.newBuilder(endpoint)
                        .timeout(Duration.ofSeconds(5))
                        .header("Authorization", "Basic " + credentials)
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(
                                HttpRequest.BodyPublishers.ofString(
                                        "grant_type=client_credentials&scope="
                                                + URLEncoder.encode(
                                                        scopes, StandardCharsets.UTF_8)))
                        .build();
        try {
            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IllegalStateException("M2M token unavailable");
            }
            var jsonMapper = mapper.readTree(response.body());
            String value = jsonMapper.path("access_token").asText();
            long ttl = jsonMapper.path("expires_in").asLong();
            if (value.isBlank() || ttl <= 30) {
                throw new IllegalStateException("Invalid token response");
            }
            cache.put(scopes, new Cached(value, Instant.now().plusSeconds(ttl)));
            return value;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Token interrupted");
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("Token unavailable");
        }
    }
}
