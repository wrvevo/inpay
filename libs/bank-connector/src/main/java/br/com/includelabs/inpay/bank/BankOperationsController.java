package br.com.includelabs.inpay.bank;

import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** API interna do conector. X-Mock true encaminha ao mock server; false chama o banco. */
@RestController
@Profile("local & !aws")
@RequestMapping("/internal/v1/bank")
public class BankOperationsController {
    private final BankSettings settings;
    private final BankConnector connector;

    public BankOperationsController(BankSettings settings, BankConnector connector) {
        this.settings = settings;
        this.connector = connector;
    }

    @GetMapping("/operations")
    @PreAuthorize("hasAuthority('SCOPE_connectors-' + @bankSecurity.scopeId() + '/read')")
    public Map<String, Object> operations() {
        return Map.of("bank", settings.id(), "data", connector.catalog());
    }

    @PostMapping("/operations/{operation}")
    @PreAuthorize("hasAuthority('SCOPE_connectors-' + @bankSecurity.scopeId() + '/execute')")
    public BankResult execute(
            @PathVariable String operation,
            @RequestHeader(value = "X-Mock", required = false) String mock,
            @RequestHeader("Idempotency-Key") String operationId,
            @RequestBody(required = false) Map<String, Object> payload) {
        return connector.execute(operation, operationId, mock, payload);
    }
}
