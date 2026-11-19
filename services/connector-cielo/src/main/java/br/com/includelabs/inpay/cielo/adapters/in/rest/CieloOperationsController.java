package br.com.includelabs.inpay.cielo.adapters.in.rest;

import br.com.includelabs.inpay.cielo.adapters.in.rest.dto.CieloOperationRequest;
import br.com.includelabs.inpay.cielo.application.CieloCollectionService;
import br.com.includelabs.inpay.cielo.domain.*;
import br.com.includelabs.inpay.security.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** Entrada autenticada para todos os métodos externos distintos da coleção oficial Cielo. */
@RestController
@RequestMapping("/internal/v1/cielo/operations")
@PreAuthorize("hasAuthority('SCOPE_connectors-cielo/execute')")
@Tag(
        name = "Cielo — coleção completa",
        description =
                "Vendas, recorrência, tokenização, Zero Auth, BIN, captura e cancelamento. Variantes de crédito/débito/boleto/transferência/wallet/AVS/fraude usam CREATE_SALE.")
@ApiResponses({
    @ApiResponse(
            responseCode = "200",
            description =
                    "Resultado nativo sanitizado; verificar httpStatus, data e uncertain, não apenas HTTP InPay."),
    @ApiResponse(responseCode = "403", description = "Escopo ou tenant inválido"),
    @ApiResponse(responseCode = "409", description = "Chave reutilizada com outro comando"),
    @ApiResponse(responseCode = "422", description = "Contrato inválido"),
    @ApiResponse(responseCode = "503", description = "Configuração indisponível")
})
public class CieloOperationsController {
    private final CieloCollectionService service;
    private final TenantContext tenants;

    /** Injeta caso de uso e contexto de lojista validado pelos filtros de segurança. */
    public CieloOperationsController(CieloCollectionService service, TenantContext tenants) {
        this.service = service;
        this.tenants = tenants;
    }

    /** Executa comandos POST/PUT externos sem permitir escolha arbitrária de URL ou método. */
    @PostMapping("/{operation}")
    @Operation(
            summary = "Executar comando da coleção Cielo",
            description =
                    "Selecione a operação no enum. CREATE_SALE preserva todos os campos nativos opcionais. Os comandos de recorrência aceitam escalares conforme documentação. Replays devolvem somente resumo; tokens e corpos nunca são persistidos.")
    public CieloResponse execute(
            @PathVariable CieloOperation operation,
            @RequestBody CieloOperationRequest request,
            @RequestHeader(value = "X-Merchant-Id", required = false) String merchant) {
        if (operation.readOnly()) {
            throw new IllegalArgumentException("Use GET for queries");
        }
        return service.execute(
                tenants.resolve(merchant),
                request.operationId(),
                operation,
                request.resourceId(),
                request.amount(),
                request.payload());
    }

    /** Consulta pagamento, recorrência, cartão tokenizado ou BIN com autorização por tenant. */
    @GetMapping("/{operation}")
    @Operation(
            summary = "Consultar recurso na Cielo",
            description =
                    "Operações: GET_PAYMENT, GET_RECURRENT, GET_CARD, QUERY_BIN. resourceId é UUID do recurso ou BIN de 6/8 dígitos.")
    public CieloResponse query(
            @PathVariable CieloOperation operation,
            @RequestParam String resourceId,
            @RequestHeader(value = "X-Merchant-Id", required = false) String merchant) {
        if (!operation.readOnly()) {
            throw new IllegalArgumentException("Use POST for commands");
        }
        return service.execute(tenants.resolve(merchant), null, operation, resourceId, null, null);
    }
}
