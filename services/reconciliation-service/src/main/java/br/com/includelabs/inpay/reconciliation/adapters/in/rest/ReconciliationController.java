package br.com.includelabs.inpay.reconciliation.adapters.in.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Contrato HTTP do InPay. Autenticação, escopos e contexto do lojista são verificados antes do
 * acesso ao caso de uso.
 */
@RestController
@Tag(
        name = "Reconciliation",
        description =
                "Contrato HTTP do InPay. Autenticação, escopos e contexto do lojista são"
                        + " verificados antes do acesso ao caso de uso.")
public class ReconciliationController {

    /**
     * Valida o acesso e sinaliza que este fluxo ainda não está implementado; a API responde HTTP
     * 501.
     *
     * @throws UnsupportedOperationException quando a validação ou a capacidade exigida impedir a
     *     conclusão da operação
     */
    @PostMapping("/internal/v1/reconciliation/run")
    @PreAuthorize("hasAuthority('SCOPE_reconciliation/run')")
    @Operation(
            summary = "Conciliação — implementação pendente",
            description =
                    "Valida o acesso e sinaliza que este fluxo ainda não está implementado; a API"
                            + " responde HTTP 501. Escopo obrigatório: reconciliation/run.")
    @ApiResponses({
        @ApiResponse(
                responseCode = "501",
                description = "Fluxo não implementado; nenhuma operação financeira é efetuada",
                content = @io.swagger.v3.oas.annotations.media.Content),
        @ApiResponse(
                responseCode = "400",
                description = "Corpo, cabeçalho ou parâmetro inválido",
                content = @io.swagger.v3.oas.annotations.media.Content),
        @ApiResponse(
                responseCode = "401",
                description = "Autenticação ausente ou inválida",
                content = @io.swagger.v3.oas.annotations.media.Content),
        @ApiResponse(
                responseCode = "403",
                description = "Escopo ou lojista não autorizado",
                content = @io.swagger.v3.oas.annotations.media.Content),
        @ApiResponse(
                responseCode = "404",
                description = "Recurso não encontrado no lojista informado",
                content = @io.swagger.v3.oas.annotations.media.Content),
        @ApiResponse(
                responseCode = "409",
                description = "Conflito de estado ou chave idempotente",
                content = @io.swagger.v3.oas.annotations.media.Content),
        @ApiResponse(
                responseCode = "422",
                description = "Regra de entrada violada",
                content = @io.swagger.v3.oas.annotations.media.Content)
    })
    public void run() {
        throw new UnsupportedOperationException("Settlement adapter pending");
    }
}
