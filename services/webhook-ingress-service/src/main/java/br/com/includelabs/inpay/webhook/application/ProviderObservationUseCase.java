package br.com.includelabs.inpay.webhook.application;

import br.com.includelabs.inpay.webhook.domain.ProviderObservation;
import br.com.includelabs.inpay.webhook.domain.port.ProviderVerificationPort;
import java.util.UUID;

/**
 * Caso de uso responsável por coordenar a regra de negócio e a porta de saída, sem depender dos
 * frameworks de infraestrutura.
 */
public final class ProviderObservationUseCase {

    private final ProviderVerificationPort port;

    /**
     * Inicializa ProviderObservationUseCase com suas dependências explícitas.
     *
     * @param port porta de saída injetada, substituível por adaptador real ou simulado
     */
    public ProviderObservationUseCase(ProviderVerificationPort port) {
        this.port = port;
    }

    /**
     * Executa o caso de uso delegando à porta de saída injetada, sem acoplar o domínio à
     * infraestrutura.
     *
     * @param observationId identificador da notificação externa que precisa ser verificada
     * @return resultado definido pelo contrato; exceções interrompem a operação sem anunciar
     *     sucesso
     */
    public ProviderObservation execute(UUID observationId) {
        return port.verify(observationId);
    }
}
