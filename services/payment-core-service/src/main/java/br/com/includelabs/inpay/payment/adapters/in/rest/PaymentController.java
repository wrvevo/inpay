package br.com.includelabs.inpay.payment.adapters.in.rest;

import br.com.includelabs.inpay.payment.adapters.in.rest.dto.CreatePaymentRequest;
import br.com.includelabs.inpay.payment.adapters.in.rest.dto.PaymentResponse;
import br.com.includelabs.inpay.payment.application.SubmitPayment;
import br.com.includelabs.inpay.payment.domain.port.PaymentStore;
import br.com.includelabs.inpay.security.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Contrato HTTP do InPay. Autenticação, escopos e contexto do lojista são verificados antes do
 * acesso ao caso de uso.
 */
@RestController
@RequestMapping("/v1/payments")
@Tag(
        name = "Payment",
        description =
                "Contrato HTTP do InPay. Autenticação, escopos e contexto do lojista são"
                        + " verificados antes do acesso ao caso de uso.")
public class PaymentController {

    private final SubmitPayment submitPayment;

    private final PaymentStore paymentStore;

    private final TenantContext tenantContext;

    /**
     * Inicializa PaymentController com suas dependências explícitas.
     *
     * @param submitPayment caso de uso responsável pelo registro idempotente do pagamento
     * @param paymentStore porta de persistência pertencente ao módulo
     * @param tenantContext resolvedor de lojista com validação de identidade e permissões
     */
    public PaymentController(
            SubmitPayment submitPayment, PaymentStore paymentStore, TenantContext tenantContext) {
        this.submitPayment = submitPayment;
        this.paymentStore = paymentStore;
        this.tenantContext = tenantContext;
    }

    /**
     * Registra o pagamento idempotente e retorna HTTP 202; esta versão permanece em CREATED sem
     * efetuar cobrança.
     *
     * @param idempotencyKey chave que permite repetir o comando sem duplicar o recurso
     * @param requestedMerchantId contexto opcional de lojista; deve corresponder à identidade
     *     autenticada
     * @param body dados de entrada da operação, sem dados sensíveis de cartão
     * @return representação pública do resultado, limitada aos dados previstos no contrato HTTP
     */
    @PostMapping
    @PreAuthorize("hasAuthority('SCOPE_payments/create')")
    @Operation(
            summary = "Registrar pagamento idempotente",
            description =
                    "Registra o pagamento idempotente e retorna HTTP 202; esta versão permanece em"
                            + " CREATED sem efetuar cobrança. Escopo obrigatório: payments/create.")
    @ApiResponses({
        @ApiResponse(
                responseCode = "202",
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
    public ResponseEntity<PaymentResponse> create(
            @RequestHeader("Idempotency-Key")
                    @Parameter(
                            description =
                                    "chave que permite repetir o comando sem duplicar o recurso")
                    String idempotencyKey,
            @RequestHeader(value = "X-Merchant-Id", required = false)
                    @Parameter(
                            description =
                                    "contexto opcional de lojista; deve corresponder à identidade"
                                            + " autenticada")
                    String requestedMerchantId,
            @Valid @RequestBody CreatePaymentRequest body) {
        return ResponseEntity.accepted()
                .body(
                        PaymentResponse.of(
                                submitPayment.execute(
                                        tenantContext.resolve(requestedMerchantId),
                                        idempotencyKey,
                                        body.merchantOrderId(),
                                        body.amount(),
                                        body.currency(),
                                        body.instrumentToken())));
    }

    /**
     * Consulta o recurso com o filtro de lojista aplicável, impedindo acesso entre tenants.
     *
     * @param id identificador do recurso, sempre validado no contexto do lojista
     * @param requestedMerchantId contexto opcional de lojista; deve corresponder à identidade
     *     autenticada
     * @return representação pública do resultado, limitada aos dados previstos no contrato HTTP
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('SCOPE_payments/read')")
    @Operation(
            summary = "Consultar recurso do lojista",
            description =
                    "Consulta o recurso com o filtro de lojista aplicável, impedindo acesso entre"
                            + " tenants. Escopo obrigatório: payments/read.")
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
    public PaymentResponse get(
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
        return PaymentResponse.of(
                paymentStore
                        .find(tenantContext.resolve(requestedMerchantId), id)
                        .orElseThrow(ApiErrors.NoSuchResource::new));
    }

    /**
     * Valida o acesso e sinaliza que este fluxo ainda não está implementado; a API responde HTTP
     * 501.
     *
     * @param id identificador do recurso, sempre validado no contexto do lojista
     * @param requestedMerchantId contexto opcional de lojista; deve corresponder à identidade
     *     autenticada
     * @throws UnsupportedOperationException quando a validação ou a capacidade exigida impedir a
     *     conclusão da operação
     */
    @PostMapping("/{id}/captures")
    @PreAuthorize("hasAuthority('SCOPE_payments/capture')")
    @Operation(
            summary = "Captura — implementação pendente",
            description =
                    "Valida o acesso e sinaliza que este fluxo ainda não está implementado; a API"
                            + " responde HTTP 501. Escopo obrigatório: payments/capture.")
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
    public void capture(
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
        owned(id, requestedMerchantId);
        throw new UnsupportedOperationException();
    }

    /**
     * Valida o acesso e sinaliza que este fluxo ainda não está implementado; a API responde HTTP
     * 501.
     *
     * @param id identificador do recurso, sempre validado no contexto do lojista
     * @param requestedMerchantId contexto opcional de lojista; deve corresponder à identidade
     *     autenticada
     * @throws UnsupportedOperationException quando a validação ou a capacidade exigida impedir a
     *     conclusão da operação
     */
    @PostMapping("/{id}/refunds")
    @PreAuthorize("hasAuthority('SCOPE_payments/refund')")
    @Operation(
            summary = "Estorno — implementação pendente",
            description =
                    "Valida o acesso e sinaliza que este fluxo ainda não está implementado; a API"
                            + " responde HTTP 501. Escopo obrigatório: payments/refund.")
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
    public void refund(
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
        owned(id, requestedMerchantId);
        throw new UnsupportedOperationException();
    }

    /**
     * Valida o acesso e sinaliza que este fluxo ainda não está implementado; a API responde HTTP
     * 501.
     *
     * @param id identificador do recurso, sempre validado no contexto do lojista
     * @param requestedMerchantId contexto opcional de lojista; deve corresponder à identidade
     *     autenticada
     * @throws UnsupportedOperationException quando a validação ou a capacidade exigida impedir a
     *     conclusão da operação
     */
    @PostMapping("/{id}/voids")
    @PreAuthorize("hasAuthority('SCOPE_payments/void')")
    @Operation(
            summary = "Desfazimento — implementação pendente",
            description =
                    "Valida o acesso e sinaliza que este fluxo ainda não está implementado; a API"
                            + " responde HTTP 501. Escopo obrigatório: payments/void.")
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
    public void cancel(
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
        owned(id, requestedMerchantId);
        throw new UnsupportedOperationException();
    }

    /**
     * Confirma que o pagamento pertence ao lojista autorizado antes de executar a operação
     * solicitada.
     *
     * @param id identificador do recurso, sempre validado no contexto do lojista
     * @param requestedMerchantId contexto opcional de lojista; deve corresponder à identidade
     *     autenticada
     */
    private void owned(UUID id, String requestedMerchantId) {
        paymentStore
                .find(tenantContext.resolve(requestedMerchantId), id)
                .orElseThrow(ApiErrors.NoSuchResource::new);
    }
}
