package br.com.includelabs.inpay.links.adapters.in.rest;

import br.com.includelabs.inpay.links.adapters.in.rest.dto.CreatePaymentLinkRequest;
import br.com.includelabs.inpay.links.application.CreateLink;
import br.com.includelabs.inpay.links.domain.PaymentLink;
import br.com.includelabs.inpay.links.domain.port.LinkStore;
import br.com.includelabs.inpay.security.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Contrato HTTP do InPay. Autenticação, escopos e contexto do lojista são verificados antes do
 * acesso ao caso de uso.
 */
@RestController
@Tag(
        name = "Link",
        description =
                "Contrato HTTP do InPay. Autenticação, escopos e contexto do lojista são"
                        + " verificados antes do acesso ao caso de uso.")
public class LinkController {

    private final CreateLink createLink;

    private final LinkStore linkStore;

    private final TenantContext tenantContext;

    /**
     * Inicializa LinkController com suas dependências explícitas.
     *
     * @param createLink caso de uso que valida e cria o link de pagamento
     * @param linkStore porta de persistência pertencente ao módulo
     * @param tenantContext resolvedor de lojista com validação de identidade e permissões
     */
    public LinkController(CreateLink createLink, LinkStore linkStore, TenantContext tenantContext) {
        this.createLink = createLink;
        this.linkStore = linkStore;
        this.tenantContext = tenantContext;
    }

    /**
     * Cria o link para o lojista autenticado com valor e expiração definidos no servidor.
     *
     * @param request dados de entrada da operação, sem dados sensíveis de cartão
     * @param requestedMerchantId contexto opcional de lojista; deve corresponder à identidade
     *     autenticada
     * @return representação pública do resultado, limitada aos dados previstos no contrato HTTP
     */
    @PostMapping("/v1/payment-links")
    @PreAuthorize("hasAuthority('SCOPE_links/write')")
    @Operation(
            summary = "Criar link de pagamento",
            description =
                    "Cria o link para o lojista autenticado com valor e expiração definidos no"
                            + " servidor. Escopo obrigatório: links/write.")
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
    public PaymentLink create(
            @RequestBody CreatePaymentLinkRequest request,
            @RequestHeader(value = "X-Merchant-Id", required = false)
                    @Parameter(
                            description =
                                    "contexto opcional de lojista; deve corresponder à identidade"
                                            + " autenticada")
                    String requestedMerchantId) {
        return createLink.execute(
                tenantContext.resolve(requestedMerchantId),
                request.merchantOrderId(),
                request.amount(),
                request.expiresAt());
    }

    /**
     * Consulta o recurso com o filtro de lojista aplicável, impedindo acesso entre tenants.
     *
     * @param id identificador do recurso, sempre validado no contexto do lojista
     * @param requestedMerchantId contexto opcional de lojista; deve corresponder à identidade
     *     autenticada
     * @return representação pública do resultado, limitada aos dados previstos no contrato HTTP
     */
    @GetMapping("/v1/payment-links/{id}")
    @PreAuthorize("hasAuthority('SCOPE_links/read')")
    @Operation(
            summary = "Consultar recurso do lojista",
            description =
                    "Consulta o recurso com o filtro de lojista aplicável, impedindo acesso entre"
                            + " tenants. Escopo obrigatório: links/read.")
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
    public PaymentLink get(
            @PathVariable
                    @Parameter(
                            description =
                                    "identificador do recurso, sempre validado no contexto do"
                                            + " lojista")
                    UUID id,
            @RequestHeader(value = "X-Merchant-Id", required = false)
                    @Parameter(
                            description =
                                    "contexto opcional de lojista; deve corresponder à identidade"
                                            + " autenticada")
                    String requestedMerchantId) {
        return linkStore
                .find(tenantContext.resolve(requestedMerchantId), id)
                .orElseThrow(ApiErrors.NoSuchResource::new);
    }

    /**
     * Revoga o recurso do lojista informado, bloqueando futuras utilizações.
     *
     * @param id identificador do recurso, sempre validado no contexto do lojista
     * @param requestedMerchantId contexto opcional de lojista; deve corresponder à identidade
     *     autenticada
     */
    @PostMapping("/v1/payment-links/{id}/revoke")
    @PreAuthorize("hasAuthority('SCOPE_links/write')")
    @Operation(
            summary = "Revogar recurso",
            description =
                    "Revoga o recurso do lojista informado, bloqueando futuras utilizações. Escopo"
                            + " obrigatório: links/write.")
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
    public void revoke(
            @PathVariable
                    @Parameter(
                            description =
                                    "identificador do recurso, sempre validado no contexto do"
                                            + " lojista")
                    UUID id,
            @RequestHeader(value = "X-Merchant-Id", required = false)
                    @Parameter(
                            description =
                                    "contexto opcional de lojista; deve corresponder à identidade"
                                            + " autenticada")
                    String requestedMerchantId) {
        UUID merchantId = tenantContext.resolve(requestedMerchantId);
        linkStore.find(merchantId, id).orElseThrow(ApiErrors.NoSuchResource::new);
        linkStore.revoke(merchantId, id);
    }

    /**
     * Revoga links vencidos ainda ativos e informa a quantidade alterada.
     *
     * @return quantidade de links revogados por expiração
     */
    @PostMapping("/internal/v1/links/expire")
    @PreAuthorize("hasAuthority('SCOPE_links/write')")
    @Operation(
            summary = "Expirar links vencidos",
            description =
                    "Revoga links vencidos ainda ativos e informa a quantidade alterada. Escopo"
                            + " obrigatório: links/write.")
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
    public Map<String, Integer> expire() {
        return Map.of("expired", linkStore.expire());
    }
}
