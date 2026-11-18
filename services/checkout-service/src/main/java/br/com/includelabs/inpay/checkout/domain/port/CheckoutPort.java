package br.com.includelabs.inpay.checkout.domain.port;

import br.com.includelabs.inpay.checkout.domain.CheckoutSession;
import java.util.UUID;

/**
 * Porta do domínio para a capacidade de CheckoutPort. Adaptadores de infraestrutura implementam
 * este contrato.
 */
public interface CheckoutPort {

    /**
     * Define o contrato de open no limite de CheckoutPort. A implementação é fornecida pelo
     * adaptador do módulo.
     *
     * @param linkId identificador do link que deve originar a sessão de checkout
     * @return resultado definido pelo contrato; exceções interrompem a operação sem anunciar
     *     sucesso
     */
    CheckoutSession open(UUID linkId);
}
