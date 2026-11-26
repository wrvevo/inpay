package br.com.includelabs.inpay.painel.application;

import br.com.includelabs.inpay.contracts.SandboxBindings;
import br.com.includelabs.inpay.painel.adapters.out.http.SandboxClients;
import br.com.includelabs.inpay.painel.adapters.out.postgres.OnboardingRepository;
import br.com.includelabs.inpay.painel.adapters.out.security.DocumentProtection;
import br.com.includelabs.inpay.painel.domain.OnboardingCase;
import br.com.includelabs.inpay.painel.domain.OnboardingDocument;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Persistent workflow with distinct document, cadastral and technical approval gates. */
@Service
public class OnboardingService {
    public static final Set<String> REQUIRED_DOCUMENTS =
            Set.of("company", "representative", "address", "bank");
    private final OnboardingRepository repo;
    private final DocumentProtection protection;
    private final SandboxClients clients;

    public OnboardingService(
            OnboardingRepository repo, DocumentProtection protection, SandboxClients clients) {
        this.repo = repo;
        this.protection = protection;
        this.clients = clients;
    }

    public record Draft(String cnpj, String legalName, String storeName, String address) {}

    public List<OnboardingCase> list(UUID tenant) {
        return repo.list(tenant);
    }

    public OnboardingCase get(UUID tenant, UUID id) {
        return repo.get(tenant, id, false);
    }

    @Transactional
    public OnboardingCase create(UUID tenant, String actor, Draft draft) {
        String cnpj = draft.cnpj() == null ? "" : draft.cnpj().replaceAll("[^0-9]", "");
        if (cnpj.length() != 14) throw new IllegalArgumentException("CNPJ required");
        var item =
                new OnboardingCase(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        cnpj,
                        text(draft.legalName(), 200),
                        text(draft.storeName(), 120),
                        text(draft.address(), 500),
                        Map.of(),
                        "DRAFT",
                        "",
                        "",
                        Map.of(),
                        "",
                        null,
                        Map.of(),
                        1);
        repo.insert(tenant, item);
        repo.audit(tenant, item.id(), actor, "CASE_CREATED");
        return item;
    }

    @Transactional
    public OnboardingCase configure(
            UUID tenant, UUID id, String actor, Map<String, String> bindings) {
        var item = locked(tenant, id, "DRAFT");
        SandboxBindings.validate(bindings);
        return update(tenant, item.configure(bindings), actor, "BINDINGS_CONFIGURED");
    }

    @Transactional
    public OnboardingDocument upload(
            UUID tenant, UUID id, String actor, String kind, String encoded) {
        locked(tenant, id, "DRAFT");
        if (!REQUIRED_DOCUMENTS.contains(kind)
                || encoded == null
                || encoded.length() > 3 * 1024 * 1024) {
            throw new IllegalArgumentException("Invalid document");
        }
        byte[] bytes = Base64.getDecoder().decode(encoded);
        try {
            var secured = protection.protect(tenant, id, kind, bytes);
            var doc =
                    repo.insertDocument(
                            tenant,
                            id,
                            kind,
                            secured.mime(),
                            secured.sha256(),
                            secured.keyId(),
                            secured.nonce(),
                            secured.ciphertext(),
                            actor,
                            secured.scanSource(),
                            secured.expiresAt());
            repo.audit(tenant, id, actor, "DOCUMENT_IMPORTED_" + kind);
            return doc;
        } finally {
            java.util.Arrays.fill(bytes, (byte) 0);
        }
    }

    public List<OnboardingDocument> documents(UUID tenant, UUID id) {
        return repo.currentDocuments(tenant, id);
    }

    @Transactional
    public Map<String, String> download(UUID tenant, UUID id, UUID docId, String actor) {
        repo.get(tenant, id, false);
        var blob = repo.document(tenant, id, docId);
        byte[] content =
                protection.decrypt(
                        tenant,
                        id,
                        blob.info().kind(),
                        blob.info().sha256(),
                        blob.keyId(),
                        blob.nonce(),
                        blob.ciphertext());
        try {
            repo.audit(tenant, id, actor, "DOCUMENT_ACCESSED");
            return Map.of(
                    "mime",
                    blob.info().mime(),
                    "data",
                    Base64.getEncoder().encodeToString(content));
        } finally {
            java.util.Arrays.fill(content, (byte) 0);
        }
    }

    @Transactional
    public OnboardingCase submit(UUID tenant, UUID id, String actor) {
        var item = locked(tenant, id, "DRAFT");
        SandboxBindings.validate(item.bindings());
        requireDocuments(tenant, id, false);
        return update(
                tenant,
                item.transition("DOCUMENT_REVIEW", actor, "", Map.of(), "", null, Map.of()),
                actor,
                "DOCUMENT_REVIEW_SUBMITTED");
    }

    @Transactional
    public void review(UUID tenant, UUID id, UUID docId, String actor, boolean approve) {
        var item = locked(tenant, id, "DOCUMENT_REVIEW");
        differentActor(item, actor);
        var doc =
                repo.currentDocuments(tenant, id).stream()
                        .filter(row -> row.id().equals(docId))
                        .findFirst()
                        .orElseThrow(() -> new IllegalArgumentException("Document is not current"));
        if (actor.equals(doc.uploadedBy()) || !doc.expiresAt().isAfter(Instant.now())) {
            throw new IllegalArgumentException("Reviewer must be independent");
        }
        repo.review(tenant, id, docId, actor, approve ? "APPROVED" : "REJECTED");
        repo.audit(tenant, id, actor, approve ? "DOCUMENT_APPROVED" : "DOCUMENT_REJECTED");
    }

    @Transactional
    public OnboardingCase partners(UUID tenant, UUID id, String actor) {
        var item = locked(tenant, id, "DOCUMENT_REVIEW");
        differentActor(item, actor);
        requireDocuments(tenant, id, true);
        var evidence = clients.partners(tenant, id);
        return update(
                tenant,
                item.transition(
                        item.state(), item.submittedBy(), actor, evidence, "", null, Map.of()),
                actor,
                "PARTNER_MOCK_REVIEWS_RECORDED");
    }

    @Transactional
    public OnboardingCase decide(UUID tenant, UUID id, String actor, boolean approve) {
        var item = locked(tenant, id, "DOCUMENT_REVIEW");
        differentActor(item, actor);
        if (approve) {
            requireDocuments(tenant, id, true);
            if (!item.partners().keySet().equals(Set.of("clearsale", "serasa-experian"))
                    || item.partners().values().stream()
                            .anyMatch(v -> !v.equals("MOCK_CUSTOMER_REVIEW"))) {
                throw new IllegalStateException("Partner evidence required");
            }
        }
        return update(
                tenant,
                item.transition(
                        approve ? "CADASTRAL_SANDBOX_APPROVED" : "DRAFT",
                        approve ? item.submittedBy() : "",
                        actor,
                        approve ? item.partners() : Map.of(),
                        "",
                        null,
                        Map.of()),
                actor,
                approve ? "SANDBOX_CADASTRAL_APPROVED" : "RETURNED_TO_DRAFT");
    }

    @Transactional
    public OnboardingCase provision(UUID tenant, UUID id, String actor) {
        var item = locked(tenant, id, "CADASTRAL_SANDBOX_APPROVED");
        requireDocuments(tenant, id, true);
        String receipt = clients.provision(tenant, item);
        return update(
                tenant,
                item.transition(
                        "SANDBOX_READY",
                        item.submittedBy(),
                        item.reviewedBy(),
                        item.partners(),
                        receipt,
                        UUID.randomUUID(),
                        Map.of()),
                actor,
                "ACCOUNTS_SANDBOX_PROVISIONED");
    }

    @Transactional
    public OnboardingCase test(UUID tenant, UUID id, String actor) {
        var item = locked(tenant, id, "SANDBOX_READY");
        requireDocuments(tenant, id, true);
        var tests = clients.tests(tenant, item);
        if (tests.isEmpty()) throw new IllegalStateException("Missing integration evidence");
        return update(
                tenant,
                item.transition(
                        "TECHNICAL_REVIEW",
                        actor,
                        item.reviewedBy(),
                        item.partners(),
                        item.accountsReceipt(),
                        item.demoId(),
                        tests),
                actor,
                "MOCK_TEST_EVIDENCE_RECORDED");
    }

    @Transactional
    public OnboardingCase technical(UUID tenant, UUID id, String actor) {
        var item = locked(tenant, id, "TECHNICAL_REVIEW");
        differentActor(item, actor);
        requireDocuments(tenant, id, true);
        if (item.tests().isEmpty() || item.accountsReceipt().isBlank() || item.demoId() == null) {
            throw new IllegalStateException("Integration evidence required");
        }
        return update(
                tenant,
                item.transition(
                        "SANDBOX_APPROVED",
                        item.submittedBy(),
                        actor,
                        item.partners(),
                        item.accountsReceipt(),
                        item.demoId(),
                        item.tests()),
                actor,
                "SANDBOX_TECHNICAL_APPROVED");
    }

    public Map<String, Object> demo(UUID tenant, UUID demoId) {
        var item = repo.getByDemo(tenant, demoId);
        if (!Set.of("SANDBOX_READY", "TECHNICAL_REVIEW", "SANDBOX_APPROVED")
                .contains(item.state())) {
            throw new IllegalArgumentException("Demo unavailable");
        }
        requireDocuments(tenant, item.id(), true);
        return Map.of(
                "demoId",
                demoId,
                "storeId",
                item.storeId(),
                "bindings",
                item.bindings(),
                "mode",
                "SYNTHETIC",
                "productionEnabled",
                false);
    }

    public List<Map<String, Object>> audit(UUID tenant, UUID id) {
        return repo.audit(tenant, id);
    }

    @Scheduled(fixedDelayString = "${INPAY_DOCUMENT_PURGE_INTERVAL_MS:3600000}")
    public void purgeExpired() {
        repo.purgeExpired();
    }

    private OnboardingCase locked(UUID tenant, UUID id, String state) {
        var item = repo.get(tenant, id, true);
        if (!state.equals(item.state()))
            throw new IllegalStateException("Case state does not permit action");
        return item;
    }

    private OnboardingCase update(UUID tenant, OnboardingCase item, String actor, String event) {
        repo.update(tenant, item);
        repo.audit(tenant, item.id(), actor, event);
        return item;
    }

    private void requireDocuments(UUID tenant, UUID id, boolean approved) {
        var docs = repo.currentDocuments(tenant, id);
        for (String kind : REQUIRED_DOCUMENTS) {
            boolean valid =
                    docs.stream()
                            .anyMatch(
                                    doc ->
                                            kind.equals(doc.kind())
                                                    && doc.expiresAt().isAfter(Instant.now())
                                                    && (!approved
                                                            || "APPROVED".equals(doc.decision())));
            if (!valid) throw new IllegalStateException("Document checklist incomplete");
        }
    }

    private static void differentActor(OnboardingCase item, String actor) {
        if (actor.equals(item.submittedBy()))
            throw new IllegalArgumentException("Independent reviewer required");
    }

    private static String text(String input, int maximum) {
        if (input == null || input.isBlank() || input.length() > maximum) {
            throw new IllegalArgumentException("Invalid case field");
        }
        return input.trim();
    }
}
