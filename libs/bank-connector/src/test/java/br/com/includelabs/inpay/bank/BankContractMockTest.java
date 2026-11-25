package br.com.includelabs.inpay.bank;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Sobe o conector contra stubs WireMock no formato JSON usado pelo Spring Cloud Contract. O
 * starter spring-cloud-contract-wiremock 4.3.1 não inicia no Spring Boot 4 porque
 * RestTemplateCustomizer saiu do pacote antigo.
 */
@SpringBootTest(
        classes = BankTestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"inpay.local-password=test-only", "inpay.bank.id=c6", "inpay.bank.mock-enabled=true"})
@ActiveProfiles("local")
class BankContractMockTest {
    private static final WireMockServer wireMock = startWireMock();

    private static WireMockServer startWireMock() {
        WireMockServer server = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        server.start();
        server.stubFor(
                WireMock.put(WireMock.urlPathMatching("^/c6/pix/v2/cob/[^/]+$"))
                        .willReturn(
                                WireMock.aResponse()
                                        .withStatus(200)
                                        .withHeader("Content-Type", "application/json")
                                        .withBody(
                                                "{\"mocked\":true,\"bank\":\"c6\",\"operation\":\"PIX_COB_PUT\",\"status\":\"ATIVA\"}")));
        return server;
    }

    @AfterAll
    static void stop() {
        wireMock.stop();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("inpay.bank.mock-base-url", () -> "http://127.0.0.1:" + wireMock.port());
    }

    @Autowired BankOperationsController controller;
    @Autowired BankConnectorConfiguration configuration;

    @Test
    void contractStubAnswersMockHeader() {
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(
                                "developer",
                                "n/a",
                                List.of(new SimpleGrantedAuthority("SCOPE_connectors-c6/execute"))));
        assertNotNull(configuration.bankRequestLog());
        BankResult result =
                controller.execute(
                        "PIX_COB_PUT", "true", "contract-1", Map.of("txid", "abc123", "valor", "10.00"));
        assertEquals(200, result.status());
        assertEquals("c6", result.bank());
        assertEquals(Boolean.TRUE, result.body().get("mocked"));
    }
}
