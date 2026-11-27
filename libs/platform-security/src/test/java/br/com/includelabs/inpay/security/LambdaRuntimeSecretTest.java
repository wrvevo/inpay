package br.com.includelabs.inpay.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class LambdaRuntimeSecretTest {
    @Test
    void readsOnlyTheSelectedModuleAndRequiresStringValues() {
        var mapper = JsonMapper.builder().build();
        String secret =
                mapper.writeValueAsString(
                        Map.of(
                                "links", Map.of("SCOPE", "links/write", "WRONG_TYPE", true),
                                "other", Map.of("CLIENT_SECRET", "test")));
        String response = mapper.writeValueAsString(Map.of("SecretString", secret));
        var settings = LambdaRuntimeSecret.moduleSettings(response, "links");
        assertEquals("links/write", settings.apply("SCOPE"));
        assertThrows(IllegalStateException.class, () -> settings.apply("CLIENT_SECRET"));
        assertThrows(IllegalStateException.class, () -> settings.apply("WRONG_TYPE"));
        assertThrows(
                IllegalStateException.class,
                () -> LambdaRuntimeSecret.moduleSettings(response, "missing"));
    }
}
