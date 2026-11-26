package br.com.includelabs.inpay.painel.domain;

import java.util.List;
import java.util.Set;

/** Catálogo de menus e escopos. O administrador só libera o que existe aqui. */
public final class Catalog {
    public static final String PASSWORD = "inpay@123";
    public static final List<String> PANEL_MENUS =
            List.of(
                    "overview",
                    "transactions",
                    "fraud",
                    "risk",
                    "clearsale",
                    "serasa",
                    "methods",
                    "billing",
                    "accounts",
                    "team",
                    "settings");
    public static final String CNPJ = "12345678000190";
    public static final String STORE_CENTRO = "11111111-1111-1111-1111-111111111111";
    public static final String STORE_SUL = "22222222-2222-2222-2222-222222222222";

    private Catalog() {}

    public static List<String> scopes(String realm, String role) {
        if ("PANEL".equals(realm) && "ADMIN".equals(role)) {
            return List.of(
                    "painel/read",
                    "painel/manage",
                    "painel/admin",
                    "payments/read",
                    "fraud/read",
                    "fraud/evaluate");
        }
        if ("PANEL".equals(realm) && "MANAGER".equals(role)) {
            return List.of("painel/read", "painel/manage", "payments/read", "fraud/read");
        }
        if ("PANEL".equals(realm)) {
            return List.of("painel/read", "payments/read");
        }
        if ("ADMIN".equals(role)) {
            return List.of(
                    "homologation/write",
                    "homologation/approve",
                    "homologation/admin",
                    "painel/read");
        }
        if ("BACKOFFICE".equals(role)) {
            return List.of("homologation/approve", "painel/read");
        }
        return List.of("homologation/write", "painel/read");
    }

    public static void requireKnownMenus(Set<String> menus) {
        if (!PANEL_MENUS.containsAll(menus)) {
            throw new IllegalArgumentException("Unknown menu");
        }
    }
}
