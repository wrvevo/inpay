package br.com.includelabs.inpay.merchant.application;

import br.com.includelabs.inpay.merchant.domain.Merchant;
import br.com.includelabs.inpay.merchant.domain.port.MerchantDirectory;
import java.util.UUID;

/**
 * Caso de uso responsável por coordenar a regra de negócio e a porta de saída, sem depender dos
 * frameworks de infraestrutura.
 */
public final class MerchantUseCase {

    private final MerchantDirectory port;

    /**
     * Inicializa MerchantUseCase com suas dependências explícitas.
     *
     * @param port porta de saída injetada, substituível por adaptador real ou simulado
     */
    public MerchantUseCase(MerchantDirectory port) {
        this.port = port;
    }

    /**
     * Executa o caso de uso delegando à porta de saída injetada, sem acoplar o domínio à
     * infraestrutura.
     *
     * @param merchantId identificador do lojista autorizado para a operação
     * @return resultado definido pelo contrato; exceções interrompem a operação sem anunciar
     *     sucesso
     */
    public Merchant execute(UUID merchantId) {
        return port.find(merchantId);
    }
}
