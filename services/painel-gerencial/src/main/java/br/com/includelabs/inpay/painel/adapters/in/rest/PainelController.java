package br.com.includelabs.inpay.painel.adapters.in.rest;

import br.com.includelabs.inpay.painel.domain.Directory;
import br.com.includelabs.inpay.painel.domain.HomologationDesk;
import java.util.Map;
import java.util.Set;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("local")
public class PainelController {
    private final Directory directory;
    private final HomologationDesk desk;

    public PainelController(Directory directory, HomologationDesk desk) {
        this.directory = directory;
        this.desk = desk;
    }

    @PostMapping("/auth/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> body) {
        try {
            return ResponseEntity.ok(
                    directory.login(body.get("realm"), body.get("login"), body.get("password")));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.status(401).body(Map.of("code", "INVALID_CREDENTIALS"));
        }
    }

    @PostMapping("/auth/recover")
    public Map<String, String> recover(@RequestBody Map<String, String> body) {
        return Map.of(
                "resetToken",
                directory.recover(body.get("login")),
                "note",
                "Laboratório local: o token aparece aqui porque não há envio de e-mail.");
    }

    @PostMapping("/auth/reset")
    public Map<String, String> reset(@RequestBody Map<String, String> body) {
        directory.reset(body.get("token"), body.get("password"));
        return Map.of("status", "UPDATED");
    }

    @PutMapping("/auth/profile")
    @PreAuthorize("isAuthenticated()")
    public Directory.SessionView profile(@RequestBody Map<String, String> body) {
        return directory.update(actor(), body.get("name"), body.get("email"), body.get("document"));
    }

    @GetMapping("/panel/v1/users")
    @PreAuthorize("hasAuthority('SCOPE_painel/read')")
    public Map<String, Object> users() {
        return Map.of("data", directory.visible(actor()));
    }

    @PostMapping("/panel/v1/users")
    @PreAuthorize(
            "hasAnyAuthority('SCOPE_painel/manage','SCOPE_painel/admin','SCOPE_homologation/admin')")
    public Directory.SessionView create(@RequestBody Map<String, Object> body) {
        return directory.create(
                actor(),
                text(body, "role"),
                text(body, "name"),
                text(body, "email"),
                text(body, "document"),
                strings(body.get("menus")),
                body.get("stores") instanceof java.util.List<?> list
                        ? list.stream().map(String::valueOf).toList()
                        : java.util.List.of());
    }

    @PutMapping("/panel/v1/users/{id}/menus")
    @PreAuthorize("hasAnyAuthority('SCOPE_painel/manage','SCOPE_painel/admin')")
    public Directory.SessionView menus(
            @PathVariable String id, @RequestBody Map<String, Object> body) {
        return directory.menus(actor(), id, strings(body.get("menus")));
    }

    @GetMapping("/homologation/v1/cases")
    @PreAuthorize(
            "hasAnyAuthority('SCOPE_homologation/write','SCOPE_homologation/approve','SCOPE_homologation/admin')")
    public Map<String, Object> cases() {
        return Map.of("data", desk.list());
    }

    @PostMapping("/homologation/v1/cases")
    @PreAuthorize("hasAnyAuthority('SCOPE_homologation/write','SCOPE_homologation/admin')")
    public HomologationDesk.Case save(@RequestBody HomologationDesk.Case body) {
        return desk.save(body);
    }

    @PostMapping("/homologation/v1/cases/{id}/submit")
    @PreAuthorize("hasAnyAuthority('SCOPE_homologation/write','SCOPE_homologation/admin')")
    public HomologationDesk.Case submit(@PathVariable String id) {
        return desk.submit(id, role(), actor());
    }

    @PostMapping("/homologation/v1/cases/{id}/decide")
    @PreAuthorize("hasAnyAuthority('SCOPE_homologation/approve','SCOPE_homologation/admin')")
    public HomologationDesk.Case decide(
            @PathVariable String id, @RequestBody Map<String, Boolean> body) {
        return desk.decide(id, role(), actor(), Boolean.TRUE.equals(body.get("approve")));
    }

    @PostMapping("/homologation/v1/cases/{id}/clone")
    @PreAuthorize("hasAnyAuthority('SCOPE_homologation/write','SCOPE_homologation/admin')")
    public HomologationDesk.Case cloneCase(
            @PathVariable String id, @RequestBody Map<String, String> body) {
        return desk.cloneFrom(id, body.get("targetStoreId"), role());
    }

    private static String actor() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }

    private static String role() {
        return SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                .map(item -> item.getAuthority())
                .filter(item -> item.startsWith("ROLE_"))
                .map(item -> item.substring(5))
                .findFirst()
                .orElse("");
    }

    private static String text(Map<String, Object> body, String key) {
        Object value = body.get(key);
        return value == null ? "" : String.valueOf(value);
    }

    private static Set<String> strings(Object value) {
        if (!(value instanceof java.util.List<?> list)) {
            return Set.of();
        }
        return list.stream().map(String::valueOf).collect(java.util.stream.Collectors.toSet());
    }
}
