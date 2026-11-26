package br.com.includelabs.inpay.clearsale.adapters.in.rest;

import br.com.includelabs.inpay.clearsale.bootstrap.ClearSaleSettings;
import br.com.includelabs.inpay.contracts.AntifraudStubs;
import br.com.includelabs.inpay.contracts.FraudMode;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Conector ClearSale. A chamada real permanece desligada; X-Mock devolve o stub. */
@RestController
public class ClearSaleController {
    private final ClearSaleSettings settings;

    public ClearSaleController(ClearSaleSettings settings) {
        this.settings = settings;
    }

    @GetMapping("/internal/v1/module")
    @PreAuthorize("hasAuthority('SCOPE_connectors-clearsale/read')")
    public Map<String, String> module() {
        return Map.of("module", "connector-clearsale", "stage", "mock", "provider", "clearsale");
    }

    @PostMapping("/internal/v1/assessments")
    @PreAuthorize("hasAuthority('SCOPE_connectors-clearsale/execute')")
    public Map<String, Object> assess(
            @RequestHeader(value = "X-Mock", required = false) String mock,
            @RequestBody(required = false) Map<String, Object> body) {
        boolean simulated = mock == null || mock.equalsIgnoreCase("true");
        if (!simulated && !settings.enabled()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "clearsale disabled");
        }
        if (!simulated) {
            throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_ENTITY, "live path not verified");
        }
        AntifraudStubs.Verdict verdict = AntifraudStubs.evaluate(FraudMode.CLEARSALE, probe(body));
        return Map.of(
                "provider",
                "clearsale",
                "score",
                verdict.score(),
                "decision",
                verdict.decision(),
                "policyVersion",
                verdict.policyVersion(),
                "reasons",
                verdict.reasons(),
                "mock",
                true);
    }

    private static AntifraudStubs.Probe probe(Map<String, Object> body) {
        return new AntifraudStubs.Probe(
                flag(body, "knownCompromised"),
                flag(body, "vpnOrProxy"),
                number(body, "declinesLastHour"),
                flag(body, "documentInconsistent"),
                flag(body, "disposableEmail"),
                flag(body, "addressMismatch"));
    }

    private static boolean flag(Map<String, Object> body, String key) {
        Object value = body == null ? null : body.get(key);
        return Boolean.TRUE.equals(value) || "true".equalsIgnoreCase(String.valueOf(value));
    }

    private static int number(Map<String, Object> body, String key) {
        Object value = body == null ? null : body.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        return 0;
    }
}
