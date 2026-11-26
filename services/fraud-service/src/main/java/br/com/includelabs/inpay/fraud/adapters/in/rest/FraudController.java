package br.com.includelabs.inpay.fraud.adapters.in.rest;

import br.com.includelabs.inpay.fraud.adapters.in.rest.dto.AssessRiskRequest;
import br.com.includelabs.inpay.fraud.application.AssessRisk;
import br.com.includelabs.inpay.fraud.domain.RiskPolicy;
import br.com.includelabs.inpay.fraud.domain.RiskSignals;
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
        name = "Fraud",
        description =
                "Contrato HTTP do InPay. Autenticação, escopos e contexto do lojista são"
                        + " verificados antes do acesso ao caso de uso.")
public class FraudController {

    private final AssessRisk assessRisk;

    private final TenantContext tenantContext;

    /**
     * Inicializa FraudController com suas dependências explícitas.
     *
     * @param assessRisk caso de uso que avalia e persiste os sinais de risco
     * @param tenantContext resolvedor de lojista com validação de identidade e permissões
     */
    public FraudController(AssessRisk assessRisk, TenantContext tenantContext) {
        this.assessRisk = assessRisk;
        this.tenantContext = tenantContext;
    }

    /**
     * Avalia os sinais recebidos pela API interna e persiste a decisão de risco explicável.
     *
     * @param request dados de entrada da operação, sem dados sensíveis de cartão
     * @param requestedMerchantId contexto opcional de lojista; deve corresponder à identidade
     *     autenticada
     * @return resultado definido pelo contrato; exceções interrompem a operação sem anunciar
     *     sucesso
     * @throws IllegalArgumentException quando a validação ou a capacidade exigida impedir a
     *     conclusão da operação
     */
    @PostMapping("/internal/v1/assessments")
    @PreAuthorize("hasAuthority('SCOPE_fraud/evaluate')")
    @Operation(
            summary = "Avaliar sinais de risco",
            description =
                    "Avalia os sinais recebidos pela API interna e persiste a decisão de risco"
                            + " explicável. Escopo obrigatório: fraud/evaluate.")
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
    public AssessRisk.Result evaluate(
            @RequestBody AssessRiskRequest request,
            @RequestHeader(value = "X-Merchant-Id", required = false)
                    @Parameter(
                            description =
                                    "contexto opcional de lojista; deve corresponder à identidade"
                                            + " autenticada")
                    String requestedMerchantId) {
        if (request.paymentId() == null) {
            throw new IllegalArgumentException();
        }
        return assessRisk.execute(
                tenantContext.resolve(requestedMerchantId),
                request.paymentId(),
                new RiskPolicy.Signals(
                        request.amount(),
                        request.attemptsLastHour(),
                        request.declinesLastHour(),
                        request.knownCompromised()),
                behavior(request.behavior()),
                device(request.device()),
                identity(request.identity()));
    }

    private static RiskSignals.Behavior behavior(AssessRiskRequest.Behavior value) {
        if (value == null) {
            return null;
        }
        return new RiskSignals.Behavior(
                value.typingIntervalMs(),
                value.mouseEntropy(),
                value.navigationSeconds(),
                value.unusualJourney());
    }

    private static RiskSignals.Device device(AssessRiskRequest.Device value) {
        if (value == null) {
            return null;
        }
        return new RiskSignals.Device(
                value.deviceKey(),
                value.country(),
                value.homeCountry(),
                value.vpnOrProxy(),
                value.seenAtOtherMerchants());
    }

    private static RiskSignals.Identity identity(AssessRiskRequest.Identity value) {
        if (value == null) {
            return null;
        }
        return new RiskSignals.Identity(
                value.cpf(),
                value.emailDomain(),
                value.deliveryMatchesBilling(),
                value.cardholderMatch(),
                value.priorPurchases());
    }
}
