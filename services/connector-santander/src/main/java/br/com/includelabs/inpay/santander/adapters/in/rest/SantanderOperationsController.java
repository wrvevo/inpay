package br.com.includelabs.inpay.santander.adapters.in.rest;

import br.com.includelabs.inpay.santander.application.ExecuteSantanderOperation;
import br.com.includelabs.inpay.santander.domain.SantanderReply;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/** REST interno do conector e o contrato usado pelo gateway. */
@RestController
@Profile("local & !aws")
public class SantanderOperationsController {
    private final ExecuteSantanderOperation useCase;

    public SantanderOperationsController(ExecuteSantanderOperation useCase) {
        this.useCase = useCase;
    }

    @GetMapping({"/internal/v1/santander/operations", "/internal/v1/bank/operations"})
    @PreAuthorize("hasAuthority('SCOPE_connectors-santander/read')")
    public Map<String, Object> operations() {
        return Map.of("bank", "santander", "data", useCase.catalog());
    }

    @PostMapping({
        "/internal/v1/santander/operations/{operation}",
        "/internal/v1/bank/operations/{operation}"
    })
    @PreAuthorize("hasAuthority('SCOPE_connectors-santander/execute')")
    public SantanderReply execute(
            @PathVariable String operation,
            @RequestHeader(value = "X-Mock", required = false) String mock,
            @RequestHeader("Idempotency-Key") String operationId,
            @RequestBody(required = false) Map<String, Object> payload) {
        return useCase.execute(operation, operationId, mock, payload);
    }
}
