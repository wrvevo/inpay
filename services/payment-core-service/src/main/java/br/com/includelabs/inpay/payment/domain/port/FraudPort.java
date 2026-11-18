package br.com.includelabs.inpay.payment.domain.port;

import java.util.UUID;

/**
 * Porta do domínio para a capacidade de FraudPort. Adaptadores de infraestrutura implementam este
 * contrato.
 */
public interface FraudPort {

    /**
     * Porta do domínio para a capacidade de Decision. Adaptadores de infraestrutura implementam
     * este contrato.
     */
    enum Decision {
        ACCEPT,
        REVIEW,
        REJECT,
        UNAVAILABLE
    }

    /**
     * Define o contrato de assess no limite de FraudPort. A implementação é fornecida pelo
     * adaptador do módulo.
     *
     * @param merchantId identificador do lojista autorizado para a operação
     * @param paymentId identificador do recurso, sempre validado no contexto do lojista
     * @param amount valor monetário em centavos; nunca utilizar ponto flutuante
     * @param instrumentToken alias opaco do instrumento ou credencial do contexto; nunca informar
     *     PAN ou CVV
     * @return resultado definido pelo contrato; exceções interrompem a operação sem anunciar
     *     sucesso
     */
    Decision assess(UUID merchantId, UUID paymentId, long amount, String instrumentToken);
}
