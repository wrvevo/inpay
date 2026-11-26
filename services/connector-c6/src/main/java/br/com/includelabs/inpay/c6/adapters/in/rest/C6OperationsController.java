package br.com.includelabs.inpay.c6.adapters.in.rest;

import br.com.includelabs.inpay.c6.application.ExecuteC6Operation;
import br.com.includelabs.inpay.c6.domain.C6Reply;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/** REST interno do conector e o contrato antigo usado pelo gateway. */
@RestController
public class C6OperationsController {
    private final ExecuteC6Operation useCase;

    /** Inicializa o caso de uso de operações do C6. */
    public C6OperationsController(ExecuteC6Operation useCase) {
        this.useCase = useCase;
    }

    /** Expõe o catálogo de operações internas do conector. */
    @GetMapping({"/internal/v1/c6/operations", "/internal/v1/bank/operations"})
    @PreAuthorize("hasAuthority('SCOPE_connectors-c6/read')")
    public Map<String, Object> operations() {
        return Map.of("bank", "c6", "data", useCase.catalog());
    }

    /** Encaminha a operação, a chave idempotente e a opção de simulação. */
    @PostMapping({
        "/internal/v1/c6/operations/{operation}",
        "/internal/v1/bank/operations/{operation}"
    })
    @PreAuthorize("hasAuthority('SCOPE_connectors-c6/execute')")
    public C6Reply execute(
            @PathVariable String operation,
            @RequestHeader(value = "X-Mock", required = false) String mock,
            @RequestHeader("Idempotency-Key") String operationId,
            @RequestBody(required = false) Map<String, Object> payload) {
        return useCase.execute(operation, operationId, mock, payload);
    }
}
