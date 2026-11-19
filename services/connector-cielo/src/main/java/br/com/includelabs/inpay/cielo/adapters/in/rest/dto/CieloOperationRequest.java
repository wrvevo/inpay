package br.com.includelabs.inpay.cielo.adapters.in.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/** Envelope para contratos Cielo com corpo objeto, escalar ou ausente. */
@Schema(
        description =
                "Operação Cielo. O payload usa o JSON nativo do endpoint; nunca informe credenciais no corpo.")
public record CieloOperationRequest(
        @Schema(
                        description = "UUID idempotente: preserve em reenvios do mesmo comando.",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                UUID operationId,
        @Schema(description = "PaymentId, RecurrentPaymentId ou MerchantOrderId conforme operação.")
                String resourceId,
        @Schema(
                        description =
                                "Valor opcional em centavos para captura/cancelamento parcial; ausente indica operação total.")
                Long amount,
        @Schema(
                        description =
                                "JSON nativo: objeto para venda/cartão/cliente; inteiro para valor/intervalo/dia/parcelas; string ISO para data; omitido para captura/cancelamento/ativação.")
                Object payload) {
    /** Evita dados de cartão em logs acidentais do DTO. */
    @Override
    public String toString() {
        return "CieloOperationRequest[operationId=" + operationId + "]";
    }
}
