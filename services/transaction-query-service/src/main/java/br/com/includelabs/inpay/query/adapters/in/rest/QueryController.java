package br.com.includelabs.inpay.query.adapters.in.rest;

import br.com.includelabs.inpay.security.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Contrato HTTP do InPay. Autenticação, escopos e contexto do lojista são verificados antes do
 * acesso ao caso de uso.
 */
@RestController
@Tag(
        name = "Query",
        description =
                "Contrato HTTP do InPay. Autenticação, escopos e contexto do lojista são"
                        + " verificados antes do acesso ao caso de uso.")
public class QueryController {

    private final JdbcTemplate jdbcTemplate;

    private final TenantContext tenantContext;

    /**
     * Inicializa QueryController com suas dependências explícitas.
     *
     * @param jdbcTemplate adaptador JDBC da base exclusiva deste serviço
     * @param tenantContext resolvedor de lojista com validação de identidade e permissões
     */
    public QueryController(JdbcTemplate jdbcTemplate, TenantContext tenantContext) {
        this.jdbcTemplate = jdbcTemplate;
        this.tenantContext = tenantContext;
    }

    /**
     * Consulta o recurso com o filtro de lojista aplicável, impedindo acesso entre tenants.
     *
     * @param id identificador do recurso, sempre validado no contexto do lojista
     * @param requestedMerchantId contexto opcional de lojista; deve corresponder à identidade
     *     autenticada
     * @return representação pública do resultado, limitada aos dados previstos no contrato HTTP
     * @throws ApiErrors.NoSuchResource quando a validação ou a capacidade exigida impedir a
     *     conclusão da operação
     */
    @GetMapping("/v1/transactions/{id}")
    @PreAuthorize("hasAuthority('SCOPE_transactions/read')")
    @Operation(
            summary = "Consultar recurso do lojista",
            description =
                    "Consulta o recurso com o filtro de lojista aplicável, impedindo acesso entre"
                            + " tenants. Escopo obrigatório: transactions/read.")
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
    public Map<String, Object> get(
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
        var rows =
                jdbcTemplate.queryForList(
                        "SELECT id,amount,status,version FROM transaction_view WHERE id=? AND"
                                + " merchant_id=?",
                        id,
                        tenantContext.resolve(requestedMerchantId));
        if (rows.isEmpty()) {
            throw new ApiErrors.NoSuchResource();
        }
        return rows.getFirst();
    }
}
