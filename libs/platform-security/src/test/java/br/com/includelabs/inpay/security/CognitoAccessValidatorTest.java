package br.com.includelabs.inpay.security;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class CognitoAccessValidatorTest {

    private Jwt token(String use, String client) {
        return Jwt.withTokenValue("test")
                .header("alg", "RS256")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .claim("token_use", use)
                .claim("client_id", client)
                .build();
    }

    @Test
    void rejectsMissingClient() {
        var token =
                Jwt.withTokenValue("test")
                        .header("alg", "RS256")
                        .expiresAt(Instant.now().plusSeconds(300))
                        .claim("token_use", "access")
                        .build();
        assertTrue(new CognitoAccessValidator(Set.of("core"), "").validate(token).hasErrors());
    }

    @Test
    void rejectsIdToken() {
        assertTrue(
                new CognitoAccessValidator(Set.of("core"), "")
                        .validate(token("id", "core"))
                        .hasErrors());
    }

    @Test
    void rejectsOtherClient() {
        assertTrue(
                new CognitoAccessValidator(Set.of("core"), "")
                        .validate(token("access", "other"))
                        .hasErrors());
    }

    @Test
    void acceptsAccess() {
        assertFalse(
                new CognitoAccessValidator(Set.of("core"), "")
                        .validate(token("access", "core"))
                        .hasErrors());
    }

    @Test
    void requiresConfiguredAudience() {
        assertTrue(
                new CognitoAccessValidator(Set.of("core"), "fraud")
                        .validate(token("access", "core"))
                        .hasErrors());
    }
}
