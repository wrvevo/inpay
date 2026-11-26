package br.com.includelabs.inpay.painel.adapters.in.rest;

import br.com.includelabs.inpay.painel.application.OnboardingService;
import br.com.includelabs.inpay.painel.domain.OnboardingCase;
import br.com.includelabs.inpay.painel.domain.OnboardingDocument;
import br.com.includelabs.inpay.security.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.json.JsonMapper;

/** Persistent onboarding API. Local fixture users and Cognito scopes are isolated. */
@RestController
@RequestMapping("/homologation/v2")
@ConditionalOnProperty(name = "inpay.onboarding.enabled", havingValue = "true")
public class OnboardingController {
    private static final String READ =
            "hasAnyAuthority('SCOPE_homologation/write','SCOPE_homologation/approve','SCOPE_homologation/admin')";
    private static final String WRITE =
            "hasAnyAuthority('SCOPE_homologation/write','SCOPE_homologation/admin')";
    private static final String APPROVE =
            "hasAnyAuthority('SCOPE_homologation/approve','SCOPE_homologation/admin')";
    private final OnboardingService service;
    private final TenantContext tenants;
    private final Environment environment;
    private final JsonMapper json = JsonMapper.builder().build();

    public OnboardingController(
            OnboardingService service, TenantContext tenants, Environment environment) {
        this.service = service;
        this.tenants = tenants;
        this.environment = environment;
    }

    public record Upload(String kind, String data) {}
    public record Decision(boolean approve) {}
    public record Bindings(Map<String, String> bindings) {}

    @GetMapping("/cases")
    @PreAuthorize(READ)
    public Map<String, Object> list() {
        actor();
        return Map.of("data", service.list(tenant()));
    }

    @PostMapping("/cases")
    @PreAuthorize(WRITE)
    public OnboardingCase create(@RequestBody OnboardingService.Draft draft) {
        return service.create(tenant(), actor(), draft);
    }

    @GetMapping("/cases/{id}")
    @PreAuthorize(READ)
    public OnboardingCase get(@PathVariable UUID id) {
        actor();
        return service.get(tenant(), id);
    }

    @PutMapping("/cases/{id}/bindings")
    @PreAuthorize(WRITE)
    public OnboardingCase configure(@PathVariable UUID id, @RequestBody Bindings body) {
        return service.configure(tenant(), id, actor(), body.bindings());
    }

    @GetMapping("/cases/{id}/documents")
    @PreAuthorize(READ)
    public Map<String, Object> documents(@PathVariable UUID id) {
        actor();
        return Map.of("data", service.documents(tenant(), id));
    }

    @PostMapping("/cases/{id}/documents")
    @PreAuthorize(WRITE)
    public OnboardingDocument upload(@PathVariable UUID id, HttpServletRequest request)
            throws IOException {
        String actor = actor();
        byte[] body = request.getInputStream().readNBytes(3 * 1024 * 1024 + 1);
        if (body.length > 3 * 1024 * 1024) throw new IllegalArgumentException("Document request too large");
        Upload upload = json.readValue(body, Upload.class);
        return service.upload(tenant(), id, actor, upload.kind(), upload.data());
    }

    @GetMapping("/cases/{id}/documents/{docId}/download")
    @PreAuthorize(APPROVE)
    public Map<String, String> download(@PathVariable UUID id, @PathVariable UUID docId) {
        return service.download(tenant(), id, docId, actor());
    }

    @PostMapping("/cases/{id}/documents/{docId}/review")
    @PreAuthorize(APPROVE)
    public Map<String, String> review(
            @PathVariable UUID id, @PathVariable UUID docId, @RequestBody Decision body) {
        service.review(tenant(), id, docId, actor(), body.approve());
        return Map.of("status", "RECORDED");
    }

    @PostMapping("/cases/{id}/submit")
    @PreAuthorize(WRITE)
    public OnboardingCase submit(@PathVariable UUID id) {
        return service.submit(tenant(), id, actor());
    }

    @PostMapping("/cases/{id}/partners")
    @PreAuthorize(APPROVE)
    public OnboardingCase partners(@PathVariable UUID id) {
        return service.partners(tenant(), id, actor());
    }

    @PostMapping("/cases/{id}/decide")
    @PreAuthorize(APPROVE)
    public OnboardingCase decide(@PathVariable UUID id, @RequestBody Decision body) {
        return service.decide(tenant(), id, actor(), body.approve());
    }

    @PostMapping("/cases/{id}/provision")
    @PreAuthorize(WRITE)
    public OnboardingCase provision(@PathVariable UUID id) {
        return service.provision(tenant(), id, actor());
    }

    @PostMapping("/cases/{id}/test")
    @PreAuthorize(WRITE)
    public OnboardingCase test(@PathVariable UUID id) {
        return service.test(tenant(), id, actor());
    }

    @PostMapping("/cases/{id}/technical")
    @PreAuthorize(APPROVE)
    public OnboardingCase technical(@PathVariable UUID id) {
        return service.technical(tenant(), id, actor());
    }

    @GetMapping("/cases/{id}/audit")
    @PreAuthorize(READ)
    public Map<String, Object> audit(@PathVariable UUID id) {
        actor();
        return Map.of("data", service.audit(tenant(), id));
    }

    @GetMapping("/demo/{demoId}")
    @PreAuthorize("hasAuthority('SCOPE_homologation/admin')")
    public Map<String, Object> demo(@PathVariable UUID demoId) {
        if (!environment.matchesProfiles("local")
                || !"inpay".equals(SecurityContextHolder.getContext().getAuthentication().getName())) {
            throw new AccessDeniedException("Local demo service only");
        }
        return service.demo(tenant(), demoId);
    }

    private UUID tenant() {
        return tenants.resolve(null);
    }

    private String actor() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (environment.matchesProfiles("local")
                && auth.getAuthorities().stream().noneMatch(a ->
                        a.getAuthority().equals("ROLE_PDV") || a.getAuthority().equals("ROLE_BACKOFFICE")
                                || a.getAuthority().equals("ROLE_ADMIN"))) {
            throw new AccessDeniedException("Named onboarding operator required");
        }
        if (auth.getName().isBlank() || auth.getName().length() > 256) {
            throw new AccessDeniedException("Invalid operator");
        }
        return auth.getName();
    }
}
