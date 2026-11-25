package br.com.includelabs.inpay.bank;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Registra um stub WireMock para cada operação de cada banco. */
public final class BankStubRegistry {
    private static final Logger LOG = LogManager.getLogger(BankStubRegistry.class);

    private BankStubRegistry() {}

    public static int register(WireMockServer server) {
        int count = 0;
        for (BankId bank : BankId.values()) {
            server.stubFor(
                    WireMock.any(WireMock.urlPathMatching("^/" + bank.slug() + "/.*"))
                            .atPriority(5)
                            .willReturn(
                                    WireMock.aResponse()
                                            .withStatus(200)
                                            .withHeader("Content-Type", "application/json")
                                            .withBody(
                                                    "{\"mocked\":true,\"bank\":\""
                                                            + bank.slug()
                                                            + "\",\"status\":\"ATIVA\",\"pixCopiaECola\":\"000201mocked\"}")));
            count++;
        }
        LOG.info("Bank mock stubs registered count={}", count);
        return count;
    }
}
