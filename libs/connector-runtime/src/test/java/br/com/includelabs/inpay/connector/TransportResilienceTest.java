package br.com.includelabs.inpay.connector;

import static org.junit.jupiter.api.Assertions.*;

import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.*;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

class TransportResilienceTest {
    @Test
    void retriesQueriesButNeverFinancialCommands() throws Exception {
        var calls = new AtomicInteger();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(
                "/",
                exchange -> {
                    calls.incrementAndGet();
                    byte[] body = "{}".getBytes();
                    exchange.sendResponseHeaders(503, body.length);
                    exchange.getResponseBody().write(body);
                    exchange.close();
                });
        server.start();
        try {
            var config =
                    new Binder(
                                    new MapConfigurationPropertySource(
                                            Map.of(
                                                    "inpay.provider.connect-timeout",
                                                    "1s",
                                                    "inpay.provider.read-timeout",
                                                    "2s")))
                            .bind("inpay.provider", Bindable.of(ProviderSettings.class))
                            .get();
            var transport = new ProviderTransport(config);
            var url = "http://127.0.0.1:" + server.getAddress().getPort();
            transport.send("GET", url, Map.of(), null, false);
            assertEquals(2, calls.get());
            transport.send("POST", url, Map.of(), Map.of("amount", 100), false);
            assertEquals(3, calls.get());
        } finally {
            server.stop(0);
        }
    }
}
