package br.com.includelabs.inpay.bin.adapters.in.rest;

import br.com.includelabs.inpay.bin.adapters.in.rest.dto.AuthorizeRequest;
import br.com.includelabs.inpay.bin.application.AuthorizePayment;
import br.com.includelabs.inpay.bin.domain.Authorization;
import br.com.includelabs.inpay.security.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
        name = "Authorization",
        description =
                "Contrato HTTP do InPay. Autenticação, escopos e contexto do lojista são"
                        + " verificados antes do acesso ao caso de uso.")
public class AuthorizationController {

    private final AuthorizePayment authorizePayment;

    private final TenantContext tenantContext;

    /**
     * Inicializa AuthorizationController com suas dependências explícitas.
     *
     * @param authorizePayment caso de uso que encaminha a autorização ao adaptador configurado
     * @param tenantContext resolvedor de lojista com validação de identidade e permissões
     */
    public AuthorizationController(AuthorizePayment authorizePayment, TenantContext tenantContext) {
        this.authorizePayment = authorizePayment;
        this.tenantContext = tenantContext;
    }

    /**
     * Encaminha a autorização ao adaptador selecionado; o perfil local é simulado e o adaptador
     * real permanece desabilitado.
     *
     * @param request dados de entrada da operação, sem dados sensíveis de cartão
     * @param requestedMerchantId contexto opcional de lojista; deve corresponder à identidade
     *     autenticada
     * @return resultado definido pelo contrato; exceções interrompem a operação sem anunciar
     *     sucesso
     */
    @PostMapping("/internal/v1/authorizations")
    @PreAuthorize("hasAuthority('SCOPE_connectors-bin/execute')")
    @Operation(
            summary = "Autorizar pagamento",
            description =
                    "Encaminha a autorização ao adaptador selecionado; o perfil local é simulado e"
                            + " o adaptador externo usa providerPayload e a configuração provider.yml. Escopo obrigatório:"
                            + " connectors-bin/execute.")
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
                content = @io.swagger.v3.oas.annotations.media.Content),
        @ApiResponse(
                responseCode = "501",
                description = "Connector externo desabilitado ou sem configuração",
                content = @io.swagger.v3.oas.annotations.media.Content)
    })
    public Authorization authorize(
            @RequestBody AuthorizeRequest request,
            @RequestHeader(value = "X-Merchant-Id", required = false)
                    @Parameter(
                            description =
                                    "contexto opcional de lojista; deve corresponder à identidade"
                                            + " autenticada")
                    String requestedMerchantId) {
        return authorizePayment.execute(
                new Authorization(
                        request.operationId(),
                        tenantContext.resolve(requestedMerchantId),
                        request.amount(),
                        request.instrumentToken(),
                        null,
                        request.providerPayload(),
                        null));
    }
}
