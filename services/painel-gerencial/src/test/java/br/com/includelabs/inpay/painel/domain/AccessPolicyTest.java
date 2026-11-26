package br.com.includelabs.inpay.painel.domain;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Set;
import org.junit.jupiter.api.Test;

class AccessPolicyTest {
    private final Directory directory = new Directory("inpay@123");

    @Test
    void exampleUsersLoginByEmailOrDocument() {
        var admin = directory.login("PANEL", "admin@inpay.local", Catalog.PASSWORD);
        assertEquals("ADMIN", admin.role());
        assertEquals(2, admin.stores().size());
        assertEquals("VIEWER", directory.login("PANEL", "10000000003", Catalog.PASSWORD).role());
        assertEquals(
                "PDV", directory.login("HOMOLOGATION", "pdv@inpay.local", Catalog.PASSWORD).role());
        assertThrows(
                IllegalArgumentException.class,
                () -> directory.login("PANEL", "pdv@inpay.local", Catalog.PASSWORD));
    }

    @Test
    void managerOnlyGrantsMenusAlreadyGranted() {
        var created =
                directory.create(
                        "manager@inpay.local",
                        "VIEWER",
                        "Nova consulta",
                        "consulta@inpay.local",
                        "10000000007",
                        Set.of("fraud", "overview"),
                        java.util.List.of(Catalog.STORE_CENTRO));
        assertEquals(Set.of("fraud", "overview"), created.menus());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        directory.create(
                                "manager@inpay.local",
                                "VIEWER",
                                "Outro",
                                "outro@inpay.local",
                                "10000000008",
                                Set.of("clearsale"),
                                java.util.List.of()));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        directory.create(
                                "viewer@inpay.local",
                                "VIEWER",
                                "Nao",
                                "nao@inpay.local",
                                "10000000009",
                                Set.of("overview"),
                                java.util.List.of()));
    }

    @Test
    void recoveryChangesPasswordAndCloneStaysInsideTheCnpj() {
        String token = directory.recover("viewer@inpay.local");
        directory.reset(token, "nova-senha-1");
        assertThrows(
                IllegalArgumentException.class,
                () -> directory.login("PANEL", "viewer@inpay.local", Catalog.PASSWORD));
        assertEquals(
                "VIEWER", directory.login("PANEL", "viewer@inpay.local", "nova-senha-1").role());
        var desk = new HomologationDesk();
        var source = desk.list().get(0);
        var cloned = desk.cloneFrom(source.id(), Catalog.STORE_SUL, "PDV");
        assertEquals(source.antifraud(), cloned.antifraud());
        assertEquals("RASCUNHO", cloned.status());
        var submitted = desk.submit(cloned.id(), "PDV", "operator");
        assertThrows(
                IllegalArgumentException.class,
                () -> desk.decide(submitted.id(), "ADMIN", "operator", true));
        assertThrows(IllegalArgumentException.class, () -> desk.save(submitted));
        assertEquals(
                "APROVADA", desk.decide(submitted.id(), "BACKOFFICE", "reviewer", true).status());
        assertFalse(
                desk.list().stream()
                        .filter(item -> item.id().equals(submitted.id()))
                        .findFirst()
                        .orElseThrow()
                        .accountsPrepared());
    }
}
