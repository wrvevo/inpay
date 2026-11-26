package br.com.includelabs.inpay.bankmock;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.stubbing.StubMapping;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

@SpringBootApplication
public class MockServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(MockServerApplication.class, args);
    }

    @Bean(destroyMethod = "stop")
    WireMockServer bankWireMock(@Value("${inpay.mock.port:8099}") int port) throws Exception {
        WireMockServer server = new WireMockServer(WireMockConfiguration.options().port(port));
        server.start();
        try {
            var resolver = new PathMatchingResourcePatternResolver();
            var mappings = resolver.getResources("classpath*:wiremock/mappings/*.json");
            if (mappings.length == 0) {
                throw new IllegalStateException("Bank mappings missing from runtime classpath");
            }
            for (var mapping : mappings) {
                try (var stream = mapping.getInputStream()) {
                    server.addStubMapping(
                            StubMapping.buildFrom(new String(stream.readAllBytes(), StandardCharsets.UTF_8)));
                }
            }
            try (var reader =
                    new BufferedReader(
                            new InputStreamReader(
                                    new ClassPathResource("intent-mappings.jsonl").getInputStream(),
                                    StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.isBlank()) {
                        server.addStubMapping(StubMapping.buildFrom(line));
                    }
                }
            }
            return server;
        } catch (Exception failure) {
            server.stop();
            throw failure;
        }
    }
}
