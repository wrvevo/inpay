package br.com.includelabs.inpay.bankmock;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import java.nio.file.Files;
import java.nio.file.Path;
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
        WireMockConfiguration options = WireMockConfiguration.options().port(port);
        Path stubs =
                Path.of("libs", "bank-connector", "src", "main", "resources", "wiremock")
                        .toAbsolutePath();
        if (Files.isDirectory(stubs)) {
            options.usingFilesUnderDirectory(stubs.toString());
        }
        WireMockServer server = new WireMockServer(options);
        server.start();
        return server;
    }
}
