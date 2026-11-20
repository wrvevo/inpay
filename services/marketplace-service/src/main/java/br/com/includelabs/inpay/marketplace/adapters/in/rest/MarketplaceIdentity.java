package br.com.includelabs.inpay.marketplace.adapters.in.rest;

import br.com.includelabs.inpay.security.TenantContext;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/** Contexto obtido de identidade validada; tenant e titular nunca são livres para o navegador. */
@Component
public class MarketplaceIdentity {
    private final TenantContext tenants;
    private final Set<String> enabled;

    /** Habilitação explícita impede ativar marketplace para todos os clientes do gateway. */
    public MarketplaceIdentity(
            TenantContext tenants, @Value("${inpay.marketplace.enabled-tenants:}") String enabled) {
        this.tenants = tenants;
        this.enabled = new HashSet<>(Arrays.asList(enabled.split(",")));
    }

    /** Resolve tenant autorizado e habilitado para este produto. */
    public UUID tenant(String requested) {
        UUID tenant = tenants.resolve(requested);
        if (!enabled.contains(tenant.toString())) {
            throw new AccessDeniedException("Marketplace not enabled for tenant");
        }
        return tenant;
    }

    /** Usa subject estável; clientes M2M sem identidade de usuário não acessam /me. */
    public String actor() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }

    /** Escopo OAuth não basta para equipe: exige grupo de função vinculado ao tenant. */
    public boolean hasRole(String role) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null
                || auth.getAuthorities().stream()
                        .noneMatch(a -> a.getAuthority().equals("SCOPE_marketplace/" + role))) {
            return false;
        }
        if (auth instanceof JwtAuthenticationToken jwt) {
            if (role.equals("customer")) {
                return jwt.getToken().getClaimAsString("username") != null;
            }
            if (jwt.getToken().getClaimAsString("username") == null) {
                return false;
            }
            java.util.List<String> groups = jwt.getToken().getClaimAsStringList("cognito:groups");
            UUID tenant = tenants.resolve(null);
            return groups != null && groups.contains("marketplace-" + role + ":" + tenant);
        }
        return true; // Basic users exist exclusively in the local profile.
    }

    /** Exige autenticação de usuário Cognito para operações de titular. */
    public String customer() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwt
                && jwt.getToken().getClaimAsString("username") == null) {
            throw new AccessDeniedException("End-user access token required");
        }
        return actor();
    }
}
