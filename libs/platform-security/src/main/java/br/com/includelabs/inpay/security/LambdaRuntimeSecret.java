package br.com.includelabs.inpay.security;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.function.Function;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Reads one module's settings from the AWS Parameters and Secrets Lambda Extension. */
public final class LambdaRuntimeSecret {
    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private LambdaRuntimeSecret() {}

    /**
     * Resolves settings for the current invocation. Existing deployments can still inject
     * individual environment variables when no secret is configured.
     */
    public static Function<String, String> settings(
            String module, boolean localRuntime, HttpClient client) {
        String secretId = System.getenv("INPAY_RUNTIME_SECRET_ID");
        if (localRuntime || secretId == null || secretId.isBlank()) {
            return LambdaRuntimeSecret::environment;
        }
        String port =
                System.getenv().getOrDefault("PARAMETERS_SECRETS_EXTENSION_HTTP_PORT", "2773");
        int extensionPort = Integer.parseInt(port);
        URI uri =
                URI.create(
                        "http://127.0.0.1:"
                                + extensionPort
                                + "/secretsmanager/get?secretId="
                                + URLEncoder.encode(secretId, StandardCharsets.UTF_8));
        HttpRequest request =
                HttpRequest.newBuilder(uri)
                        .timeout(Duration.ofSeconds(3))
                        .header("X-Aws-Parameters-Secrets-Token", environment("AWS_SESSION_TOKEN"))
                        .GET()
                        .build();
        String responseBody;
        try {
            HttpResponse<String> response =
                    client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IllegalStateException(
                        "Runtime secret unavailable; HTTP " + response.statusCode());
            }
            responseBody = response.body();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Runtime secret request interrupted");
        } catch (IOException exception) {
            throw new IllegalStateException("Runtime secret could not be read");
        }
        return moduleSettings(responseBody, module);
    }

    static Function<String, String> moduleSettings(String responseBody, String module) {
        JsonNode settings;
        try {
            String payload = MAPPER.readTree(responseBody).path("SecretString").asText();
            settings = MAPPER.readTree(payload);
        } catch (tools.jackson.core.JacksonException exception) {
            throw new IllegalStateException("Runtime secret contains invalid JSON");
        }
        if (!settings.isObject()) {
            throw new IllegalStateException("Runtime secret must contain a JSON object");
        }
        return name -> {
            JsonNode setting = settings.get(module + "." + name);
            if (setting == null) {
                setting = settings.get(name);
            }
            if (setting == null) {
                setting = settings.path(module).path(name);
            }
            return required(name, setting.isString() ? setting.asText() : null);
        };
    }

    private static String environment(String name) {
        return required(name, System.getenv(name));
    }

    private static String required(String name, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing " + name);
        }
        return value;
    }
}
