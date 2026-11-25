package br.com.includelabs.inpay.bankmock;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class MockServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(MockServerApplication.class, args);
    }

    @Bean(destroyMethod = "stop")
    WireMockServer bankWireMock(@Value("${inpay.mock.port:8099}") int port) {
        WireMockServer server =
                new WireMockServer(
                        WireMockConfiguration.options().port(port).usingFilesUnderClasspath("wiremock"));
        server.start();
        return server;
    }
}
