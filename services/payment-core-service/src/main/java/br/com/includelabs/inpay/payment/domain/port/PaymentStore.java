package br.com.includelabs.inpay.payment.domain.port;

import br.com.includelabs.inpay.payment.domain.Payment;
import java.util.*;

/**
 * Porta do domínio para a capacidade de PaymentStore. Adaptadores de infraestrutura implementam
 * este contrato.
 */
public interface PaymentStore {

    /**
     * Registra ou recupera o pagamento de forma idempotente; reutilização da chave com payload
     * diferente deve gerar conflito.
     *
     * @param payment agregado de pagamento com identidade, valor e último estado confirmado
     * @param idempotencyKey chave que permite repetir o comando sem duplicar o recurso
     * @param fingerprint SHA-256 do comando canônico, usado para detectar reutilização indevida da
     *     chave
     * @return resultado definido pelo contrato; exceções interrompem a operação sem anunciar
     *     sucesso
     */
    Payment register(Payment payment, String idempotencyKey, String fingerprint);

    /**
     * Consulta o recurso com o filtro de lojista aplicável, impedindo acesso entre tenants.
     *
     * @param merchantId identificador do lojista autorizado para a operação
     * @param paymentId identificador do recurso, sempre validado no contexto do lojista
     * @return recurso encontrado no lojista informado, ou Optional vazio quando ausente
     */
    Optional<Payment> find(UUID merchantId, UUID paymentId);
}
