package br.com.includelabs.inpay.token.adapters.in.rest;

import br.com.includelabs.inpay.security.*;
import br.com.includelabs.inpay.token.adapters.in.rest.dto.CreateSimulationInstrumentRequest;
import br.com.includelabs.inpay.token.application.CreateSimulationToken;
import br.com.includelabs.inpay.token.domain.Instrument;
import br.com.includelabs.inpay.token.domain.port.InstrumentStore;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Contrato HTTP do InPay. Autenticação, escopos e contexto do lojista são verificados antes do
 * acesso ao caso de uso.
 */
@RestController
@Profile("local")
@Tag(
        name = "Token",
        description =
                "Contrato HTTP do InPay. Autenticação, escopos e contexto do lojista são"
                        + " verificados antes do acesso ao caso de uso.")
public class TokenController {

    private final InstrumentStore instrumentStore;

    private final TenantContext tenantContext;

    /**
     * Inicializa TokenController com suas dependências explícitas.
     *
     * @param instrumentStore porta de persistência pertencente ao módulo
     * @param tenantContext resolvedor de lojista com validação de identidade e permissões
     */
    public TokenController(InstrumentStore instrumentStore, TenantContext tenantContext) {
        this.instrumentStore = instrumentStore;
        this.tenantContext = tenantContext;
    }

    /**
     * Cria um alias de instrumento simulado no perfil local, sem coletar dados reais de cartão.
     *
     * @param request dados de entrada da operação, sem dados sensíveis de cartão
     * @param requestedMerchantId contexto opcional de lojista; deve corresponder à identidade
     *     autenticada
     * @return representação pública do resultado, limitada aos dados previstos no contrato HTTP
     */
    @PostMapping("/local/v1/instruments")
    @PreAuthorize("hasAuthority('SCOPE_tokens/write')")
    @Operation(
            summary = "Criar instrumento simulado",
            description =
                    "Cria um alias de instrumento simulado no perfil local, sem coletar dados reais"
                            + " de cartão. Escopo obrigatório: tokens/write.")
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
    public Instrument create(
            @RequestBody CreateSimulationInstrumentRequest request,
            @RequestHeader(value = "X-Merchant-Id", required = false)
                    @Parameter(
                            description =
                                    "contexto opcional de lojista; deve corresponder à identidade"
                                            + " autenticada")
                    String requestedMerchantId) {
        return new CreateSimulationToken(instrumentStore)
                .execute(
                        tenantContext.resolve(requestedMerchantId),
                        request.provider(),
                        request.affiliationId());
    }

    /**
     * Consulta o recurso com o filtro de lojista aplicável, impedindo acesso entre tenants.
     *
     * @param token alias opaco do instrumento ou credencial do contexto; nunca informar PAN ou CVV
     * @param requestedMerchantId contexto opcional de lojista; deve corresponder à identidade
     *     autenticada
     * @return representação pública do resultado, limitada aos dados previstos no contrato HTTP
     */
    @GetMapping("/internal/v1/instruments/{token}")
    @PreAuthorize("hasAuthority('SCOPE_tokens/resolve')")
    @Operation(
            summary = "Consultar recurso do lojista",
            description =
                    "Consulta o recurso com o filtro de lojista aplicável, impedindo acesso entre"
                            + " tenants. Escopo obrigatório: tokens/resolve.")
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
    public Instrument read(
            @PathVariable
                    @Parameter(
                            description =
                                    "alias opaco do instrumento ou credencial do contexto; nunca"
                                            + " informar PAN ou CVV")
                    String token,
            @RequestHeader(value = "X-Merchant-Id", required = false)
                    @Parameter(
                            description =
                                    "contexto opcional de lojista; deve corresponder à identidade"
                                            + " autenticada")
                    String requestedMerchantId) {
        return instrumentStore
                .find(tenantContext.resolve(requestedMerchantId), token)
                .orElseThrow(ApiErrors.NoSuchResource::new);
    }

    /**
     * Revoga o recurso do lojista informado, bloqueando futuras utilizações.
     *
     * @param token alias opaco do instrumento ou credencial do contexto; nunca informar PAN ou CVV
     * @param requestedMerchantId contexto opcional de lojista; deve corresponder à identidade
     *     autenticada
     */
    @DeleteMapping("/v1/instruments/{token}")
    @PreAuthorize("hasAuthority('SCOPE_tokens/write')")
    @Operation(
            summary = "Revogar recurso",
            description =
                    "Revoga o recurso do lojista informado, bloqueando futuras utilizações. Escopo"
                            + " obrigatório: tokens/write.")
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
                                    "alias opaco do instrumento ou credencial do contexto; nunca"
                                            + " informar PAN ou CVV")
                    String token,
            @RequestHeader(value = "X-Merchant-Id", required = false)
                    @Parameter(
                            description =
                                    "contexto opcional de lojista; deve corresponder à identidade"
                                            + " autenticada")
                    String requestedMerchantId) {
        instrumentStore.revoke(tenantContext.resolve(requestedMerchantId), token);
    }
}
