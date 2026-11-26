package br.com.includelabs.inpay.painel.domain;

import java.util.Set;

/** Quem pode criar usuários e quais menus essa pessoa pode repassar. */
public final class AccessPolicy {
    private AccessPolicy() {}

    public static void grantPanel(
            String actorRole, Set<String> actorMenus, String targetRole, Set<String> requested) {
        Catalog.requireKnownMenus(requested);
        if ("ADMIN".equals(actorRole)) {
            if (!Set.of("ADMIN", "MANAGER", "VIEWER").contains(targetRole)) {
                throw new IllegalArgumentException("Role not assignable");
            }
            return;
        }
        if (!"MANAGER".equals(actorRole)) {
            throw new IllegalArgumentException("Role cannot create users");
        }
        if (!Set.of("MANAGER", "VIEWER").contains(targetRole)) {
            throw new IllegalArgumentException("Manager cannot assign this role");
        }
        if (!actorMenus.containsAll(requested)) {
            throw new IllegalArgumentException("Menu outside manager grant");
        }
    }

    public static void grantHomologation(String actorRole, String targetRole) {
        if (!"ADMIN".equals(actorRole)
                || !Set.of("ADMIN", "PDV", "BACKOFFICE").contains(targetRole)) {
            throw new IllegalArgumentException("Role not assignable");
        }
    }
}
