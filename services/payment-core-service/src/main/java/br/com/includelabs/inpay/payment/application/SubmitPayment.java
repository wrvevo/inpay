package br.com.includelabs.inpay.payment.application;

import br.com.includelabs.inpay.payment.domain.Payment;
import br.com.includelabs.inpay.payment.domain.port.PaymentStore;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

/** Registra um pagamento idempotente; não autoriza nem captura valores em uma adquirente. */
public final class SubmitPayment {

    private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 128;

    private final PaymentStore paymentStore;

    /**
     * @param paymentStore porta responsável pela gravação atômica do pagamento, idempotência e
     *     outbox
     */
    public SubmitPayment(PaymentStore paymentStore) {
        this.paymentStore = paymentStore;
    }

    /**
     * Valida o comando, calcula sua impressão digital e solicita o registro atômico. Repetir a
     * chave com o mesmo comando recupera o pagamento existente; alterar o comando causa conflito.
     *
     * @param merchantId lojista já autorizado pelo contexto de segurança
     * @param idempotencyKey chave fornecida pelo integrador para esta operação
     * @param merchantOrderId referência do pedido no sistema do lojista
     * @param amount valor positivo em centavos
     * @param currency moeda da operação; BRL nesta versão
     * @param instrumentToken alias opaco do instrumento, nunca PAN ou CVV
     * @return pagamento criado ou recuperado pela idempotência
     * @throws IllegalArgumentException se a chave ou os dados do pagamento forem inválidos
     * @throws IllegalStateException se a chave já tiver sido usada com outro comando
     */
    public Payment execute(
            UUID merchantId,
            String idempotencyKey,
            String merchantOrderId,
            long amount,
            String currency,
            String instrumentToken) {
        validateIdempotencyKey(idempotencyKey);
        Payment payment =
                new Payment(
                        UUID.randomUUID(),
                        merchantId,
                        merchantOrderId,
                        amount,
                        currency,
                        instrumentToken,
                        Payment.Status.CREATED,
                        0,
                        0,
                        false,
                        0);
        String fingerprint = fingerprint(merchantOrderId, amount, currency, instrumentToken);
        return paymentStore.register(payment, idempotencyKey, fingerprint);
    }

    /** Rejeita chaves ausentes ou acima do limite aceito pela tabela de idempotência. */
    private void validateIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null
                || idempotencyKey.isBlank()
                || idempotencyKey.length() > MAX_IDEMPOTENCY_KEY_LENGTH) {
            throw new IllegalArgumentException("Invalid idempotency key");
        }
    }

    /** Calcula SHA-256 sobre a mesma representação canônica utilizada na versão anterior. */
    private String fingerprint(
            String merchantOrderId, long amount, String currency, String instrumentToken) {
        String canonicalRequest =
                lengthPrefixed(merchantOrderId)
                        + amount
                        + ":"
                        + lengthPrefixed(currency)
                        + lengthPrefixed(instrumentToken);
        try {
            byte[] digest =
                    MessageDigest.getInstance("SHA-256")
                            .digest(canonicalRequest.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "SHA-256 must be available in the Java runtime", exception);
        }
    }

    /** Prefixa o comprimento para evitar ambiguidades na concatenação dos campos. */
    private static String lengthPrefixed(String value) {
        return value.length() + ":" + value;
    }
}
