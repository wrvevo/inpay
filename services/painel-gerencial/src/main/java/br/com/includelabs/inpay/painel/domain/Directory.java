package br.com.includelabs.inpay.painel.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Diretório de laboratório. Reinicia com os usuários de exemplo. */
public final class Directory {
    public record Store(String id, String name, String cnpj) {}

    public record Person(
            String id,
            String realm,
            String role,
            String name,
            String email,
            String document,
            String password,
            Set<String> menus,
            List<String> stores) {}

    public record SessionView(
            String token,
            String id,
            String realm,
            String role,
            String name,
            String email,
            String document,
            Set<String> menus,
            List<Store> stores) {}

    private final Map<String, Person> people = new LinkedHashMap<>();
    private final Map<String, Store> stores = new LinkedHashMap<>();
    private final Map<String, String> resets = new ConcurrentHashMap<>();
    private final String secret;

    public Directory(String secret) {
        this.secret = secret;
        stores.put(
                Catalog.STORE_CENTRO, new Store(Catalog.STORE_CENTRO, "Loja Centro", Catalog.CNPJ));
        stores.put(Catalog.STORE_SUL, new Store(Catalog.STORE_SUL, "Loja Sul", Catalog.CNPJ));
        add(
                panel(
                        "Administrador InPay",
                        "admin@inpay.local",
                        "10000000001",
                        "ADMIN",
                        Catalog.PANEL_MENUS,
                        both()));
        add(
                panel(
                        "Gerente da loja",
                        "manager@inpay.local",
                        "10000000002",
                        "MANAGER",
                        List.of("overview", "transactions", "fraud", "risk", "team"),
                        List.of(Catalog.STORE_CENTRO)));
        add(
                panel(
                        "Consulta da loja",
                        "viewer@inpay.local",
                        "10000000003",
                        "VIEWER",
                        List.of("overview", "transactions"),
                        both()));
        add(homolog("PDV InPay", "pdv@inpay.local", "10000000004", "PDV"));
        add(homolog("Backoffice InPay", "backoffice@inpay.local", "10000000005", "BACKOFFICE"));
        add(homolog("Admin de homologação", "homologacao@inpay.local", "10000000006", "ADMIN"));
    }

    public SessionView login(String realm, String login, String password) {
        Person person = find(login);
        if (person == null
                || password == null
                || !person.realm().equals(realm)
                || !java.util.Arrays.equals(
                        person.password().getBytes(java.nio.charset.StandardCharsets.UTF_8),
                        password.getBytes(java.nio.charset.StandardCharsets.UTF_8))) {
            throw new IllegalArgumentException("Invalid credentials");
        }
        String token =
                br.com.includelabs.inpay.security.LocalApiToken.issue(
                        secret,
                        person.email(),
                        person.role(),
                        person.realm(),
                        Catalog.scopes(person.realm(), person.role()),
                        Instant.now().getEpochSecond() + 12 * 3600);
        return view(token, person);
    }

    public String recover(String login) {
        Person person = find(login);
        if (person == null) {
            throw new IllegalArgumentException("Invalid credentials");
        }
        String token = UUID.randomUUID().toString();
        resets.put(token, person.id());
        return token;
    }

    public void reset(String token, String password) {
        String id = resets.remove(token);
        if (id == null || password == null || password.length() < 8) {
            throw new IllegalArgumentException("Invalid reset");
        }
        Person current = people.get(id);
        people.put(
                id,
                copy(
                        current,
                        current.name(),
                        current.email(),
                        current.document(),
                        password,
                        current.menus()));
    }

    public SessionView update(String actorEmail, String name, String email, String document) {
        Person actor = byEmail(actorEmail);
        if (name.isBlank() || email.isBlank() || document.isBlank()) {
            throw new IllegalArgumentException("Invalid profile");
        }
        Person updated =
                copy(
                        actor,
                        name.trim(),
                        email.trim().toLowerCase(Locale.ROOT),
                        document.trim(),
                        actor.password(),
                        actor.menus());
        people.put(actor.id(), updated);
        return view("", updated);
    }

    public List<SessionView> visible(String actorEmail) {
        Person actor = byEmail(actorEmail);
        List<SessionView> result = new ArrayList<>();
        for (Person person : people.values()) {
            if (person.realm().equals(actor.realm())) {
                result.add(view("", person));
            }
        }
        return result;
    }

    public SessionView create(
            String actorEmail,
            String role,
            String name,
            String email,
            String document,
            Set<String> menus,
            List<String> storeIds) {
        Person actor = byEmail(actorEmail);
        if ("PANEL".equals(actor.realm())) {
            AccessPolicy.grantPanel(actor.role(), actor.menus(), role, menus);
        } else {
            AccessPolicy.grantHomologation(actor.role(), role);
            menus = Set.of();
        }
        if (find(email) != null || find(document) != null) {
            throw new IllegalArgumentException("User already exists");
        }
        Person created =
                new Person(
                        UUID.randomUUID().toString(),
                        actor.realm(),
                        role,
                        name,
                        email.toLowerCase(Locale.ROOT),
                        document,
                        Catalog.PASSWORD,
                        Set.copyOf(menus),
                        List.copyOf(
                                storeIds == null || storeIds.isEmpty()
                                        ? actor.stores()
                                        : storeIds));
        people.put(created.id(), created);
        return view("", created);
    }

    public SessionView menus(String actorEmail, String userId, Set<String> menus) {
        Person actor = byEmail(actorEmail);
        Person target = people.get(userId);
        if (target == null || !target.realm().equals(actor.realm())) {
            throw new IllegalArgumentException("User not found");
        }
        AccessPolicy.grantPanel(actor.role(), actor.menus(), target.role(), menus);
        Person updated =
                copy(
                        target,
                        target.name(),
                        target.email(),
                        target.document(),
                        target.password(),
                        menus);
        people.put(userId, updated);
        return view("", updated);
    }

    private SessionView view(String token, Person person) {
        List<Store> selected = person.stores().stream().map(stores::get).toList();
        return new SessionView(
                token,
                person.id(),
                person.realm(),
                person.role(),
                person.name(),
                person.email(),
                person.document(),
                person.menus(),
                selected);
    }

    private Person find(String login) {
        if (login == null) {
            return null;
        }
        String key = login.trim().toLowerCase(Locale.ROOT);
        for (Person person : people.values()) {
            if (person.email().equalsIgnoreCase(key) || person.document().equals(login.trim())) {
                return person;
            }
        }
        return null;
    }

    private Person byEmail(String email) {
        Person person = find(email);
        if (person == null) {
            throw new IllegalArgumentException("User not found");
        }
        return person;
    }

    private void add(Person person) {
        people.put(person.id(), person);
    }

    private static Person panel(
            String name,
            String email,
            String document,
            String role,
            List<String> menus,
            List<String> stores) {
        return new Person(
                email,
                "PANEL",
                role,
                name,
                email,
                document,
                Catalog.PASSWORD,
                new LinkedHashSet<>(menus),
                stores);
    }

    private static Person homolog(String name, String email, String document, String role) {
        return new Person(
                email,
                "HOMOLOGATION",
                role,
                name,
                email,
                document,
                Catalog.PASSWORD,
                Set.of(),
                both());
    }

    private static List<String> both() {
        return List.of(Catalog.STORE_CENTRO, Catalog.STORE_SUL);
    }

    private static Person copy(
            Person current,
            String name,
            String email,
            String document,
            String password,
            Set<String> menus) {
        return new Person(
                current.id(),
                current.realm(),
                current.role(),
                name,
                email,
                document,
                password,
                Set.copyOf(menus),
                current.stores());
    }
}
