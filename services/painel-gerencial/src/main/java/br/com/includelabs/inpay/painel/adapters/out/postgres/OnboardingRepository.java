package br.com.includelabs.inpay.painel.adapters.out.postgres;

import br.com.includelabs.inpay.painel.domain.OnboardingCase;
import br.com.includelabs.inpay.painel.domain.OnboardingDocument;
import br.com.includelabs.inpay.security.ApiErrors;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

/** All lookups are scoped by the authenticated tenant. Mutations lock the case row. */
@Repository
public class OnboardingRepository {
    private final JdbcTemplate jdbc;
    private final JsonMapper json = JsonMapper.builder().build();

    /** Inicializa o adaptador de persistência PostgreSQL. */
    public OnboardingRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Lista os processos do tenant em ordem de criação. */
    public List<OnboardingCase> list(UUID tenant) {
        return jdbc.query(
                "SELECT payload::text FROM onboarding_case WHERE tenant=? ORDER BY created_at DESC LIMIT 100",
                (rs, n) -> json.readValue(rs.getString(1), OnboardingCase.class),
                tenant);
    }

    /** Consulta o processo e aplica bloqueio de linha quando solicitado. */
    public OnboardingCase get(UUID tenant, UUID id, boolean lock) {
        var rows =
                jdbc.query(
                        "SELECT payload::text FROM onboarding_case WHERE tenant=? AND id=?"
                                + (lock ? " FOR UPDATE" : ""),
                        (rs, n) -> json.readValue(rs.getString(1), OnboardingCase.class),
                        tenant,
                        id);
        if (rows.isEmpty()) throw new ApiErrors.NoSuchResource();
        return rows.getFirst();
    }

    /** Localiza o processo associado à demonstração do tenant. */
    public OnboardingCase getByDemo(UUID tenant, UUID demoId) {
        var rows =
                jdbc.query(
                        "SELECT payload::text FROM onboarding_case WHERE tenant=? AND payload->>'demoId'=?",
                        (rs, n) -> json.readValue(rs.getString(1), OnboardingCase.class),
                        tenant,
                        demoId.toString());
        if (rows.isEmpty()) throw new ApiErrors.NoSuchResource();
        return rows.getFirst();
    }

    /** Persiste um novo processo de homologação. */
    public void insert(UUID tenant, OnboardingCase item) {
        jdbc.update(
                "INSERT INTO onboarding_case(tenant,id,payload) VALUES(?,?,?::jsonb)",
                tenant,
                item.id(),
                json.writeValueAsString(item));
    }

    /** Atualiza o processo e exige que o registro exista no tenant. */
    public void update(UUID tenant, OnboardingCase item) {
        int count =
                jdbc.update(
                        "UPDATE onboarding_case SET payload=?::jsonb,updated_at=now() WHERE tenant=? AND id=?",
                        json.writeValueAsString(item),
                        tenant,
                        item.id());
        if (count != 1) throw new ApiErrors.NoSuchResource();
    }

    /** Registra ou consulta eventos de auditoria do processo. */
    public void audit(UUID tenant, UUID id, String actor, String event) {
        jdbc.update(
                "INSERT INTO onboarding_audit(tenant,case_id,actor,event) VALUES(?,?,?,?)",
                tenant,
                id,
                actor,
                event);
    }

    /** Registra ou consulta eventos de auditoria do processo. */
    public List<Map<String, Object>> audit(UUID tenant, UUID id) {
        get(tenant, id, false);
        return jdbc.queryForList(
                "SELECT actor,event,created_at FROM onboarding_audit WHERE tenant=? AND case_id=? ORDER BY id DESC LIMIT 100",
                tenant,
                id);
    }

    public record Blob(OnboardingDocument info, String keyId, byte[] nonce, byte[] ciphertext) {}

    /** Persiste a versão criptografada e os metadados do documento. */
    public OnboardingDocument insertDocument(
            UUID tenant,
            UUID id,
            String kind,
            String mime,
            String sha256,
            String keyId,
            byte[] nonce,
            byte[] ciphertext,
            String actor,
            String source,
            Instant expiresAt) {
        int version =
                jdbc.queryForObject(
                        "SELECT COALESCE(MAX(version),0)+1 FROM onboarding_document WHERE tenant=? AND case_id=? AND kind=?",
                        Integer.class,
                        tenant,
                        id,
                        kind);
        UUID docId = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO onboarding_document(tenant,case_id,id,kind,version,mime,sha256,key_id,nonce,ciphertext,uploaded_by,scan_source,expires_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)",
                tenant,
                id,
                docId,
                kind,
                version,
                mime,
                sha256,
                keyId,
                nonce,
                ciphertext,
                actor,
                source,
                OffsetDateTime.ofInstant(expiresAt, ZoneOffset.UTC));
        return new OnboardingDocument(
                docId, kind, version, mime, sha256, source, actor, "PENDING", expiresAt);
    }

    /** Seleciona a última versão de cada tipo de documento. */
    public List<OnboardingDocument> currentDocuments(UUID tenant, UUID id) {
        get(tenant, id, false);
        return jdbc.query(
                "SELECT DISTINCT ON(kind) * FROM onboarding_document WHERE tenant=? AND case_id=? ORDER BY kind,version DESC",
                (rs, n) -> metadata(rs),
                tenant,
                id);
    }

    /** Recupera o conteúdo criptografado ainda válido no tenant. */
    public Blob document(UUID tenant, UUID id, UUID docId) {
        var rows =
                jdbc.query(
                        "SELECT * FROM onboarding_document WHERE tenant=? AND case_id=? AND id=? AND ciphertext IS NOT NULL AND expires_at>now()",
                        (rs, n) ->
                                new Blob(
                                        metadata(rs),
                                        rs.getString("key_id"),
                                        rs.getBytes("nonce"),
                                        rs.getBytes("ciphertext")),
                        tenant,
                        id,
                        docId);
        if (rows.isEmpty()) throw new ApiErrors.NoSuchResource();
        return rows.getFirst();
    }

    /** Persiste a decisão de revisão do documento vigente. */
    public void review(UUID tenant, UUID id, UUID docId, String actor, String decision) {
        int count =
                jdbc.update(
                        "UPDATE onboarding_document SET decision=?,reviewed_by=? WHERE tenant=? AND case_id=? AND id=? AND expires_at>now() AND ciphertext IS NOT NULL",
                        decision,
                        actor,
                        tenant,
                        id,
                        docId);
        if (count != 1) throw new ApiErrors.NoSuchResource();
    }

    /** Remove o conteúdo criptografado dos documentos expirados. */
    public int purgeExpired() {
        return jdbc.update(
                "UPDATE onboarding_document SET ciphertext=NULL,nonce=NULL,decision='EXPIRED' WHERE expires_at<=now() AND ciphertext IS NOT NULL");
    }

    /** Converte os dados da consulta em metadados do documento. */
    private static OnboardingDocument metadata(ResultSet rs) throws SQLException {
        return new OnboardingDocument(
                rs.getObject("id", UUID.class),
                rs.getString("kind"),
                rs.getInt("version"),
                rs.getString("mime"),
                rs.getString("sha256"),
                rs.getString("scan_source"),
                rs.getString("uploaded_by"),
                rs.getString("decision"),
                rs.getObject("expires_at", OffsetDateTime.class).toInstant());
    }
}
