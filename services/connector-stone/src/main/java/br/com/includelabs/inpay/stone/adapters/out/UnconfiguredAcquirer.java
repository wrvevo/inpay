package br.com.includelabs.inpay.stone.adapters.out;

import br.com.includelabs.inpay.stone.domain.Authorization;
import br.com.includelabs.inpay.stone.domain.port.AcquirerPort;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Ponto de extensão ainda não implementado. Falha explicitamente para impedir resultados
 * financeiros fictícios.
 */
@Component
@Profile("!local")
public class UnconfiguredAcquirer implements AcquirerPort {

    /**
     * Interrompe a operação porque o adaptador real ainda não foi implementado. Não produz sucesso
     * financeiro simulado.
     *
     * @param request dados de entrada da operação, sem dados sensíveis de cartão
     * @return resultado definido pelo contrato; exceções interrompem a operação sem anunciar
     *     sucesso
     * @throws UnsupportedOperationException quando a validação ou a capacidade exigida impedir a
     *     conclusão da operação
     */
    public Authorization authorize(Authorization request) {
        throw new UnsupportedOperationException(
                "Provider adapter requires implementation and homologation");
    }
}
