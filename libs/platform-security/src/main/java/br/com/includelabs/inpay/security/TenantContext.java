package br.com.includelabs.inpay.security;

import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * Componente de infraestrutura do InPay responsável por TenantContext. Não deve ser referenciado
 * pelo domínio.
 */
@Component
public class TenantContext {

    private final Environment environment;

    private final String bindings;

    private final Set<String> trusted;

    /**
     * Inicializa TenantContext com suas dependências explícitas.
     *
     * @param environment ambiente de configuração e perfis Spring ativos
     * @param bindings mapeamento configurado de client IDs para lojistas
     * @param trusted client IDs dos serviços internos autorizados a transportar contexto de lojista
     */
    public TenantContext(
            Environment environment,
            @Value("${inpay.security.client-tenants:}") String bindings,
            @Value("${inpay.security.trusted-services:}") String trusted) {
        this.environment = environment;
        this.bindings = bindings;
        this.trusted = SecurityConfiguration.csv(trusted);
    }

    /**
     * Resolve o lojista a partir da identidade autenticada e rejeita contexto de tenant não
     * autorizado.
     *
     * @param requested identificador opcional do lojista solicitado; não é aceito sem validar a
     *     identidade
     * @return identificador do lojista autorizado
     * @throws AccessDeniedException quando a validação ou a capacidade exigida impedir a conclusão
     *     da operação
     */
    public UUID resolve(String requested) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AccessDeniedException("Authentication required");
        }
        UUID tenant = null;
        if (environment.matchesProfiles("local")) {
            tenant = UUID.fromString(environment.getRequiredProperty("inpay.local-merchant"));
        } else if (authentication instanceof JwtAuthenticationToken jwt) {
            String client = jwt.getToken().getClaimAsString("client_id");
            for (String binding : bindings.split(",")) {
                String[] parts = binding.trim().split("=", 2);
                if (parts.length == 2 && parts[0].equals(client)) {
                    tenant = UUID.fromString(parts[1]);
                }
            }
            // Internal services may carry tenant context; destination still checks resource
            // ownership.
            if (tenant == null && trusted.contains(client) && requested != null) {
                tenant = UUID.fromString(requested);
            }
        }
        if (tenant == null || (requested != null && !tenant.equals(UUID.fromString(requested)))) {
            throw new AccessDeniedException("Tenant not authorized");
        }
        return tenant;
    }
}
