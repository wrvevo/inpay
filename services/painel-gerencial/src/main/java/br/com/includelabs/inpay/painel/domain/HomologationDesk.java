package br.com.includelabs.inpay.painel.domain;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Homologação por loja. Lojas do mesmo CNPJ podem copiar a configuração uma da outra. */
public final class HomologationDesk {
    public record Case(
            String id,
            String storeId,
            String cnpj,
            String legalName,
            String storeName,
            String email,
            String phone,
            String address,
            Set<String> payments,
            String antifraud,
            String settlementAlias,
            boolean splitEnabled,
            String status,
            boolean accountsPrepared) {}

    private final Map<String, Case> cases = new LinkedHashMap<>();

    public HomologationDesk() {
        save(
                draft(
                        Catalog.STORE_CENTRO,
                        "InPay Comércio de Laboratório Ltda",
                        "Loja Centro",
                        "EM_HOMOLOGACAO"));
        save(
                draft(
                        Catalog.STORE_SUL,
                        "InPay Comércio de Laboratório Ltda",
                        "Loja Sul",
                        "RASCUNHO"));
    }

    public List<Case> list() {
        return List.copyOf(cases.values());
    }

    public Case save(Case item) {
        String id =
                item.id() == null || item.id().isBlank() ? UUID.randomUUID().toString() : item.id();
        if (!Set.of("RASCUNHO", "EM_HOMOLOGACAO", "APROVADA", "RECUSADA").contains(item.status())) {
            throw new IllegalArgumentException("Invalid status");
        }
        Case stored =
                new Case(
                        id,
                        item.storeId(),
                        digits(item.cnpj()),
                        item.legalName(),
                        item.storeName(),
                        item.email(),
                        item.phone(),
                        item.address(),
                        Set.copyOf(item.payments()),
                        item.antifraud(),
                        item.settlementAlias(),
                        item.splitEnabled(),
                        item.status(),
                        item.accountsPrepared());
        cases.put(id, stored);
        return stored;
    }

    public Case submit(String id, String role) {
        if (!Set.of("PDV", "ADMIN").contains(role)) {
            throw new IllegalArgumentException("Role cannot submit");
        }
        Case current = required(id);
        return save(copy(current, "EM_HOMOLOGACAO", false));
    }

    public Case decide(String id, String role, boolean approve) {
        if (!Set.of("BACKOFFICE", "ADMIN").contains(role)) {
            throw new IllegalArgumentException("Role cannot approve");
        }
        Case current = required(id);
        if (!current.status().equals("EM_HOMOLOGACAO")) {
            throw new IllegalArgumentException("Case is not waiting");
        }
        return save(copy(current, approve ? "APROVADA" : "RECUSADA", approve));
    }

    public Case cloneFrom(String sourceId, String targetStoreId, String role) {
        if (!Set.of("PDV", "ADMIN").contains(role)) {
            throw new IllegalArgumentException("Role cannot clone");
        }
        Case source = required(sourceId);
        Case target =
                cases.values().stream()
                        .filter(item -> item.storeId().equals(targetStoreId))
                        .findFirst()
                        .orElseThrow(() -> new IllegalArgumentException("Target store not found"));
        if (!source.cnpj().equals(target.cnpj()) || source.storeId().equals(target.storeId())) {
            throw new IllegalArgumentException("Clone requires another store of the same CNPJ");
        }
        return save(
                new Case(
                        target.id(),
                        target.storeId(),
                        target.cnpj(),
                        source.legalName(),
                        target.storeName(),
                        source.email(),
                        source.phone(),
                        source.address(),
                        source.payments(),
                        source.antifraud(),
                        source.settlementAlias(),
                        source.splitEnabled(),
                        "RASCUNHO",
                        false));
    }

    private Case required(String id) {
        Case found = cases.get(id);
        if (found == null) {
            throw new IllegalArgumentException("Case not found");
        }
        return found;
    }

    private static Case draft(String storeId, String legalName, String storeName, String status) {
        return new Case(
                storeId,
                storeId,
                Catalog.CNPJ,
                legalName,
                storeName,
                "contato@inpay.local",
                "11999990000",
                "Rua do Laboratório, 100",
                Set.of("pix", "credit_card", "boleto"),
                "INPAY_RISK",
                "conta-liquidacao-local",
                false,
                status,
                false);
    }

    private static Case copy(Case current, String status, boolean prepared) {
        return new Case(
                current.id(),
                current.storeId(),
                current.cnpj(),
                current.legalName(),
                current.storeName(),
                current.email(),
                current.phone(),
                current.address(),
                current.payments(),
                current.antifraud(),
                current.settlementAlias(),
                current.splitEnabled(),
                status,
                prepared);
    }

    private static String digits(String value) {
        String digits = value == null ? "" : value.replaceAll("\\D", "");
        if (digits.length() != 14) {
            throw new IllegalArgumentException("Invalid CNPJ");
        }
        return digits;
    }
}
