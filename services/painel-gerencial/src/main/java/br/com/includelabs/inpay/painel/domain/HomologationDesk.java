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
    private final Map<String, String> submittedBy = new LinkedHashMap<>();

    /** Inicializa os registros demonstrativos da homologação legada. */
    public HomologationDesk() {
        store(
                draft(
                        Catalog.STORE_CENTRO,
                        "InPay Comércio de Laboratório Ltda",
                        "Loja Centro",
                        "RASCUNHO"));
        store(
                draft(
                        Catalog.STORE_SUL,
                        "InPay Comércio de Laboratório Ltda",
                        "Loja Sul",
                        "RASCUNHO"));
    }

    /** Lista os processos de laboratório armazenados em memória. */
    public synchronized List<Case> list() {
        return List.copyOf(cases.values());
    }

    /** Valida e atualiza o processo em edição. */
    public synchronized Case save(Case item) {
        Case existing = cases.get(item.id());
        if (existing != null && !editable(existing)) {
            throw new IllegalArgumentException("Case is locked for review");
        }
        if (!"RASCUNHO".equals(item.status()) || item.accountsPrepared()) {
            throw new IllegalArgumentException(
                    "Draft cannot set approval or Accounts provisioning");
        }
        if (existing != null && !existing.storeId().equals(item.storeId())) {
            throw new IllegalArgumentException("Store cannot change");
        }
        return store(item);
    }

    /** Armazena o processo demonstrativo no diretório local. */
    private Case store(Case item) {
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

    /** Submete a jornada para revisão com validação do operador. */
    public synchronized Case submit(String id, String role, String actor) {
        if (!Set.of("PDV", "ADMIN").contains(role)) {
            throw new IllegalArgumentException("Role cannot submit");
        }
        Case current = required(id);
        if (!editable(current)
                || actor == null
                || actor.isBlank()
                || current.legalName() == null
                || current.legalName().isBlank()
                || current.address() == null
                || current.address().isBlank()
                || current.payments().isEmpty()) {
            throw new IllegalArgumentException("Case is incomplete or locked");
        }
        submittedBy.put(id, actor);
        return store(copy(current, "EM_HOMOLOGACAO", false));
    }

    /** Registra a aprovação ou recusa da jornada demonstrativa. */
    public synchronized Case decide(String id, String role, String actor, boolean approve) {
        if (!Set.of("BACKOFFICE", "ADMIN").contains(role)) {
            throw new IllegalArgumentException("Role cannot approve");
        }
        Case current = required(id);
        if (!current.status().equals("EM_HOMOLOGACAO")
                || !submittedBy.containsKey(id)
                || actor == null
                || actor.isBlank()
                || actor.equals(submittedBy.get(id))) {
            throw new IllegalArgumentException("Case is not waiting");
        }
        submittedBy.remove(id);
        // Approval of the laboratory record never confirms Accounts or production activation.
        return store(copy(current, approve ? "APROVADA" : "RECUSADA", false));
    }

    /** Cria outra loja de laboratório a partir de um cadastro existente. */
    public synchronized Case cloneFrom(String sourceId, String targetStoreId, String role) {
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
        if (!editable(target)) {
            throw new IllegalArgumentException("Target store is locked");
        }
        return store(
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

    /** Verifica se o processo ainda permite alterações. */
    private static boolean editable(Case item) {
        return Set.of("RASCUNHO", "RECUSADA").contains(item.status());
    }

    /** Localiza o processo ou rejeita um identificador inexistente. */
    private Case required(String id) {
        Case found = cases.get(id);
        if (found == null) {
            throw new IllegalArgumentException("Case not found");
        }
        return found;
    }

    /** Cria um rascunho de homologação com valores iniciais. */
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

    /** Copia o processo com o novo estado e a situação da demo. */
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

    /** Remove caracteres não numéricos do documento. */
    private static String digits(String value) {
        String digits = value == null ? "" : value.replaceAll("\\D", "");
        if (digits.length() != 14) {
            throw new IllegalArgumentException("Invalid CNPJ");
        }
        return digits;
    }
}
