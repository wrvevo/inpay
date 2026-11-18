package br.com.includelabs.gateway.links.adapters.in.rest;

import java.util.Map;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
@RestController
public class ModuleController {
@GetMapping("/internal/v1/module") @PreAuthorize("hasAuthority('SCOPE_links/read')")
public Map<String,String> module() { return Map.of("module","payment-links-service","stage","development-scaffold"); }
}
