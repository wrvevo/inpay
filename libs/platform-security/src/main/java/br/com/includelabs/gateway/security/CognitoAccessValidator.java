package br.com.includelabs.gateway.security;

import java.util.Set;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jwt.Jwt;
public record CognitoAccessValidator(Set<String> allowedClients, String audience)
 implements OAuth2TokenValidator<Jwt> {
 public OAuth2TokenValidatorResult validate(Jwt token) {
  boolean valid = token.getExpiresAt() != null && "access".equals(token.getClaimAsString("token_use"))
   && token.getClaimAsString("client_id") != null
   && allowedClients.contains(token.getClaimAsString("client_id"))
   && (audience.isBlank() || (token.getAudience()!=null && token.getAudience().contains(audience)));
  return valid ? OAuth2TokenValidatorResult.success() : OAuth2TokenValidatorResult.failure(
   new OAuth2Error("invalid_token", "Token not valid for this API", null));
 }
}
