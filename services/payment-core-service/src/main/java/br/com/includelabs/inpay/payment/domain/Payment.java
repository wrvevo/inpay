package br.com.includelabs.inpay.payment.domain;

import java.util.UUID;

/**
 * Representa o estado financeiro de um pagamento em centavos de real. As transições são imutáveis:
 * cada operação devolve uma nova versão. Esta classe não chama adquirentes; seus métodos devem ser
 * usados após confirmar o resultado externo.
 */
public record Payment(
        UUID id,
        UUID merchantId,
        String merchantOrderId,
        long amount,
        String currency,
        String instrumentToken,
        Status status,
        long captured,
        long refunded,
        boolean uncertain,
        long version) {

    /**
     * Modelo ou regra de negócio do módulo InPay, independente de Spring e da infraestrutura de
     * persistência.
     */
    public enum Status {
        CREATED,
        AUTHORIZED,
        CAPTURED,
        PARTIALLY_REFUNDED,
        REFUNDED,
        VOIDED,
        DECLINED
    }

    /** Valida os dados mínimos e impede saldos negativos ou superiores ao valor autorizado. */
    public Payment {
        boolean invalidIdentity =
                id == null
                        || merchantId == null
                        || merchantOrderId == null
                        || merchantOrderId.isBlank();
        boolean invalidInstrument = instrumentToken == null || !instrumentToken.startsWith("ptok_");
        if (invalidIdentity || amount <= 0 || !"BRL".equals(currency) || invalidInstrument) {
            throw new IllegalArgumentException("Invalid payment");
        }
        if (captured < 0 || captured > amount || refunded < 0 || refunded > captured) {
            throw new IllegalArgumentException("Invalid balances");
        }
    }

    /**
     * Registra uma autorização confirmada para um pagamento ainda não autorizado.
     *
     * @return nova versão no estado AUTHORIZED
     * @throws IllegalStateException se a operação anterior for incerta ou o estado não for CREATED
     */
    public Payment authorize() {
        if (uncertain || status != Status.CREATED) {
            throw new IllegalStateException("Cannot authorize");
        }
        return copyWith(Status.AUTHORIZED, captured, refunded, false);
    }

    /**
     * Registra captura confirmada, limitada ao saldo autorizado e condicionada à aprovação de
     * risco. Esta versão aceita uma captura por pagamento, inclusive quando parcial.
     *
     * @param captureAmount valor capturado, em centavos
     * @param riskAccepted indica se a decisão antifraude autorizou a captura
     * @return pagamento com o valor capturado atualizado
     * @throws IllegalStateException se o estado, o risco ou o saldo impedirem a captura
     */
    public Payment capture(long captureAmount, boolean riskAccepted) {
        long availableAmount = amount - captured;
        boolean invalidAmount = captureAmount <= 0 || captureAmount > availableAmount;
        if (uncertain || !riskAccepted || status != Status.AUTHORIZED || invalidAmount) {
            throw new IllegalStateException("Capture blocked");
        }
        return copyWith(Status.CAPTURED, captured + captureAmount, refunded, false);
    }

    /**
     * Registra um estorno confirmado sem ultrapassar o total capturado.
     *
     * @param refundAmount valor do estorno, em centavos
     * @return pagamento parcialmente ou totalmente estornado
     * @throws IllegalStateException se não houver saldo capturado disponível ou a operação estiver
     *     incerta
     */
    public Payment refund(long refundAmount) {
        boolean refundableState = status == Status.CAPTURED || status == Status.PARTIALLY_REFUNDED;
        long refundableAmount = captured - refunded;
        if (uncertain || !refundableState || refundAmount <= 0 || refundAmount > refundableAmount) {
            throw new IllegalStateException("Refund blocked");
        }
        long totalRefunded = refunded + refundAmount;
        Status nextStatus = totalRefunded == captured ? Status.REFUNDED : Status.PARTIALLY_REFUNDED;
        return copyWith(nextStatus, captured, totalRefunded, false);
    }

    /**
     * Registra o desfazimento confirmado de uma autorização ainda não capturada.
     *
     * @return pagamento no estado VOIDED
     * @throws IllegalStateException se já houver captura, incerteza ou estado incompatível
     */
    public Payment voidAuthorization() {
        if (uncertain || status != Status.AUTHORIZED || captured != 0) {
            throw new IllegalStateException("Void blocked");
        }
        return copyWith(Status.VOIDED, captured, refunded, false);
    }

    /**
     * Bloqueia novas transições financeiras até que o resultado externo seja esclarecido.
     *
     * @return nova versão marcada como incerta, preservando o último estado confirmado
     */
    public Payment markUnknown() {
        return copyWith(status, captured, refunded, true);
    }

    /** Cria a próxima versão sem modificar a identidade, o lojista ou o instrumento original. */
    private Payment copyWith(
            Status nextStatus, long capturedAmount, long refundedAmount, boolean isUncertain) {
        return new Payment(
                id,
                merchantId,
                merchantOrderId,
                amount,
                currency,
                instrumentToken,
                nextStatus,
                capturedAmount,
                refundedAmount,
                isUncertain,
                version + 1);
    }
}
