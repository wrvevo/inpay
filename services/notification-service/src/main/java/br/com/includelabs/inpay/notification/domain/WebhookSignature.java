package br.com.includelabs.inpay.notification.domain;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Modelo ou regra de negócio do módulo InPay, independente de Spring e da infraestrutura de
 * persistência.
 */
public final class WebhookSignature {

    /**
     * Assina timestamp e bytes exatos do corpo com HMAC-SHA256, exigindo segredo de pelo menos 256
     * bits.
     *
     * @param secret segredo obtido por configuração segura; não registrar em logs
     * @param timestamp timestamp incluído na assinatura do webhook
     * @param body dados de entrada da operação, sem dados sensíveis de cartão
     * @return resultado definido pelo contrato; exceções interrompem a operação sem anunciar
     *     sucesso
     * @throws IllegalArgumentException quando a validação ou a capacidade exigida impedir a
     *     conclusão da operação
     * @throws IllegalStateException quando a validação ou a capacidade exigida impedir a conclusão
     *     da operação
     */
    public String sign(byte[] secret, long timestamp, byte[] body) {
        if (secret.length < 32) {
            throw new IllegalArgumentException("Use at least 256-bit secret");
        }
        try {
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            mac.update((timestamp + ".").getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(mac.doFinal(body));
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
