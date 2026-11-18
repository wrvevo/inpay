package br.com.includelabs.inpay.security;

import java.util.Set;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Componente de infraestrutura do InPay responsável por CognitoAccessValidator. Não deve ser
 * referenciado pelo domínio.
 */
public record CognitoAccessValidator(Set<String> allowedClients, String audience)
        implements OAuth2TokenValidator<Jwt> {

    /**
     * Valida claims específicos do Cognito; assinatura, emissor e validade temporal são verificados
     * pelo decoder configurado.
     *
     * @param token alias opaco do instrumento ou credencial do contexto; nunca informar PAN ou CVV
     * @return resultado de validação, com erro quando algum claim obrigatório não corresponder
     */
    public OAuth2TokenValidatorResult validate(Jwt token) {
        boolean valid =
                token.getExpiresAt() != null
                        && "access".equals(token.getClaimAsString("token_use"))
                        && token.getClaimAsString("client_id") != null
                        && allowedClients.contains(token.getClaimAsString("client_id"))
                        && (audience.isBlank()
                                || (token.getAudience() != null
                                        && token.getAudience().contains(audience)));
        return valid
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(
                        new OAuth2Error("invalid_token", "Token not valid for this API", null));
    }
}
