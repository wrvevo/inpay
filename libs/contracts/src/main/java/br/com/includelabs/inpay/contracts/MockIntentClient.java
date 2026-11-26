package br.com.includelabs.inpay.contracts;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Set;

/** Synthetic-only transport. No customer payload, provider credentials or live fallback. */
public final class MockIntentClient {
    private static final Duration TIMEOUT = Duration.ofSeconds(3);
    private static final HttpClient HTTP =
            HttpClient.newBuilder()
                    .connectTimeout(TIMEOUT)
                    .followRedirects(HttpClient.Redirect.NEVER)
                    .build();

    private MockIntentClient() {}

    public record Reply(int status, String body) {}

    public static boolean configured() {
        return !System.getenv().getOrDefault("INPAY_INTENT_MOCK_URL", "").isBlank();
    }

    public static Reply execute(String provider, String intent)
            throws IOException, InterruptedException {
        URI base = URI.create(System.getenv().getOrDefault("INPAY_INTENT_MOCK_URL", ""));
        if (!"http".equals(base.getScheme())
                || base.getHost() == null
                || !Set.of("localhost", "127.0.0.1", "bank-mock-server").contains(base.getHost())
                || base.getUserInfo() != null
                || base.getQuery() != null
                || base.getFragment() != null
                || !Set.of("", "/").contains(base.getPath())
                || !provider.matches("[a-z0-9-]+")
                || !intent.matches("[A-Z0-9_]+")) {
            throw new IllegalStateException("Invalid local mock endpoint or intent");
        }
        HttpResponse<String> response =
                HTTP.send(
                        HttpRequest.newBuilder(base.resolve("/intents/" + provider + "/" + intent))
                                .timeout(TIMEOUT)
                                .header("Accept", "application/json")
                                .POST(HttpRequest.BodyPublishers.noBody())
                                .build(),
                        HttpResponse.BodyHandlers.ofString());
        return new Reply(response.statusCode(), response.body());
    }
}
