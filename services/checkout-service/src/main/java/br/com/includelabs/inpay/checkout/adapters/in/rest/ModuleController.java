package br.com.includelabs.inpay.checkout.adapters.in.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Contrato HTTP do InPay. Autenticação, escopos e contexto do lojista são verificados antes do
 * acesso ao caso de uso.
 */
@RestController
@Tag(
        name = "Module",
        description =
                "Contrato HTTP do InPay. Autenticação, escopos e contexto do lojista são"
                        + " verificados antes do acesso ao caso de uso.")
public class ModuleController {

    /**
     * Informa a identidade e o estágio do módulo; não comprova que integrações externas estejam
     * operacionais.
     *
     * @return representação pública do resultado, limitada aos dados previstos no contrato HTTP
     */
    @GetMapping("/internal/v1/module")
    @PreAuthorize("hasAuthority('SCOPE_checkout/read')")
    @Operation(
            summary = "Consultar identidade e estágio do módulo",
            description =
                    "Informa a identidade e o estágio do módulo; não comprova que integrações"
                            + " externas estejam operacionais. Escopo obrigatório: checkout/read.")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "Resultado da operação conforme schema de resposta"),
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
    public Map<String, String> module() {
        return Map.of("module", "checkout-service", "stage", "development-scaffold");
    }
}
