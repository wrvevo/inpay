package br.com.includelabs.gateway.checkout.adapters.in.rest;

import java.util.Map;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
@RestController
public class ModuleController {
@GetMapping("/internal/v1/module") @PreAuthorize("hasAuthority('SCOPE_checkout/read')")
public Map<String,String> module() { return Map.of("module","checkout-service","stage","development-scaffold"); }
}
