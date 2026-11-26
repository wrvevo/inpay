package br.com.includelabs.inpay.bankmock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.tomakehurst.wiremock.WireMockServer;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class MockServerApplicationTest {
    @Test
    void packagedMappingsAnswerBankAndRiskIntentsAndRejectUnknownPaths() throws Exception {
        WireMockServer server = new MockServerApplication().bankWireMock(0);
        try {
            assertEquals(445, server.getStubMappings().size());
            HttpClient client = HttpClient.newHttpClient();
            assertResponse(client, server, "/c6/pix/v2/cob/synthetic", "PUT", 200, "PIX_COB_PUT");
            assertResponse(client, server, "/intents/c6/BOLEPIX_CREATE", "POST", 200, "mocked");
            assertResponse(client, server, "/intents/itau/SISPAG_PIX_POST", "POST", 200, "mocked");
            assertResponse(
                    client, server, "/intents/clearsale/ASSESSMENT_ACCEPT", "POST", 200, "ACCEPT");
            assertResponse(
                    client,
                    server,
                    "/intents/serasa-experian/DOCUMENT_PENDING",
                    "POST",
                    422,
                    "DOCUMENT_PENDING");
            assertResponse(
                    client,
                    server,
                    "/intents/clearsale/PROVIDER_UNAVAILABLE",
                    "POST",
                    503,
                    "PROVIDER_UNAVAILABLE");
            assertResponse(client, server, "/intents/rede/AUTHORIZE", "POST", 200, "MOCK_AUTHORIZE");
            assertResponse(client, server, "/intents/cielo/ZERO_AUTH", "POST", 200, "MOCK_ZERO_AUTH");
            assertResponse(client, server, "/intents/itau/UNDECLARED", "POST", 404, "");
        } finally {
            server.stop();
        }
    }

    private static void assertResponse(
            HttpClient client,
            WireMockServer server,
            String path,
            String method,
            int expected,
            String marker)
            throws Exception {
        var request =
                HttpRequest.newBuilder(URI.create(server.baseUrl() + path))
                        .timeout(Duration.ofSeconds(5))
                        .method(method, HttpRequest.BodyPublishers.noBody())
                        .build();
        var reply = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(expected, reply.statusCode(), path);
        assertTrue(reply.body().contains(marker), path);
    }
}
