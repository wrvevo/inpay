package br.com.includelabs.inpay.billing.bootstrap;

import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * Acesso global da equipe financeira exige grupo dedicado; scope isolado não concede visão de todos
 * os clientes.
 */
@Component("billingIdentity")
public class BillingIdentity {
    private final Set<String> eventClients;

    public BillingIdentity(@Value("${inpay.billing.event-clients:}") String clients) {
        eventClients = new HashSet<>(Arrays.asList(clients.split(",")));
    }

    public String actor() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }

    /**
     * Operador e revisor são identidades distintas; serviço de eventos possui allowlist própria.
     */
    public boolean allowed(String role) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null
                || auth.getAuthorities().stream()
                        .noneMatch(a -> a.getAuthority().equals("SCOPE_billing/" + role)))
            return false;
        if (auth instanceof JwtAuthenticationToken jwt) {
            if (role.equals("erp"))
                return true; // Tenant binding is enforced by the ERP controller.
            if (role.equals("events"))
                return jwt.getToken().getClaimAsString("username") == null
                        && eventClients.contains(jwt.getToken().getClaimAsString("client_id"));
            List<String> groups = jwt.getToken().getClaimAsStringList("cognito:groups");
            return jwt.getToken().getClaimAsString("username") != null
                    && groups != null
                    && groups.contains(
                            role.equals("approve") ? "inpay-finance-reviewer" : "inpay-finance");
        }
        return true; // Basic authentication is configured only in the local profile.
    }
}
