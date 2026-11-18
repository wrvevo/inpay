package br.com.includelabs.gateway.webhook.adapters.in.rest;

import java.util.Map;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
@RestController
public class ModuleController {
@GetMapping("/internal/v1/module") @PreAuthorize("hasAuthority('SCOPE_provider-events/read')")
public Map<String,String> module() { return Map.of("module","webhook-ingress-service","stage","development-scaffold"); }
}
