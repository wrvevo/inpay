package br.com.includelabs.inpay.getnet.adapters.in.rest;

import br.com.includelabs.inpay.connector.*;
import br.com.includelabs.inpay.connector.domain.*;
import br.com.includelabs.inpay.security.TenantContext;
import io.swagger.v3.oas.annotations.*;
import io.swagger.v3.oas.annotations.responses.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** Entrada REST autenticada para as operações externas do connector Getnet. */
@RestController
@RequestMapping("/internal/v1/provider-payments")
@PreAuthorize("hasAuthority('SCOPE_connectors-getnet/execute')")
@Tag(
        name = "Getnet API externa",
        description =
                "Payload nativo documentado em resources/provider-examples. O status retornado é nativo, não o HTTP da InPay.")
@ApiResponses({
    @ApiResponse(
            responseCode = "200",
            description = "Resultado nativo; uncertain=true exige consulta"),
    @ApiResponse(responseCode = "403", description = "Escopo ou estabelecimento inválido"),
    @ApiResponse(responseCode = "409", description = "Chave reutilizada com outro comando"),
    @ApiResponse(responseCode = "422", description = "Payload inválido"),
    @ApiResponse(
            responseCode = "503",
            description = "Connector desabilitado ou configuração/autenticação inválida")
})
public class ProviderPaymentsController {

    private final ProviderService service;

    private final TenantContext tenants;

    /** Injeta caso de uso externo e identidade verificada por Cognito. */
    public ProviderPaymentsController(ProviderService service, TenantContext tenants) {
        this.service = service;
        this.tenants = tenants;
    }

    /** Autoriza com o payload nativo; a chave deve ser mantida em reenvios do mesmo comando. */
    @PostMapping
    @Operation(summary = "Autorizar pagamento na API Getnet")
    public ProviderResult authorize(
            @RequestBody ProviderRequest request,
            @RequestHeader(value = "X-Merchant-Id", required = false) String merchant) {
        return service.execute(
                tenants.resolve(merchant),
                request.operationId(),
                ProviderOperation.AUTHORIZE,
                null,
                request.payload());
    }

    /** Executa captura, cancelamento ou estorno no identificador retornado pela adquirente. */
    @PostMapping("/{id}/{operation}")
    @Operation(
            summary = "Capturar, cancelar ou estornar pagamento",
            description =
                    "operation: CAPTURE, CANCEL ou REFUND. Use um novo operationId para cada operação distinta.")
    public ProviderResult change(
            @PathVariable String id,
            @PathVariable ProviderOperation operation,
            @RequestBody ProviderRequest request,
            @RequestHeader(value = "X-Merchant-Id", required = false) String merchant) {
        if (operation == ProviderOperation.AUTHORIZE || operation == ProviderOperation.QUERY) {
            throw new IllegalArgumentException("Invalid operation on payment");
        }
        return service.execute(
                tenants.resolve(merchant), request.operationId(), operation, id, request.payload());
    }

    /**
     * Consulta o estado externo sem reutilizar ou sobrescrever o resultado idempotente original.
     */
    @GetMapping("/{id}")
    @Operation(summary = "Consultar pagamento na API Getnet")
    public ProviderResult query(
            @PathVariable String id,
            @RequestHeader(value = "X-Merchant-Id", required = false) String merchant) {
        return service.execute(
                tenants.resolve(merchant),
                UUID.randomUUID(),
                ProviderOperation.QUERY,
                id,
                Map.of());
    }
}
