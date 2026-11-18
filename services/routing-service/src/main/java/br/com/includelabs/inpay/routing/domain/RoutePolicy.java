package br.com.includelabs.inpay.routing.domain;

import java.util.*;

/**
 * Modelo ou regra de negócio do módulo InPay, independente de Spring e da infraestrutura de
 * persistência.
 */
public final class RoutePolicy {

    /**
     * Modelo ou regra de negócio do módulo InPay, independente de Spring e da infraestrutura de
     * persistência.
     */
    public record Candidate(String provider, String affiliation, boolean enabled, int priority) {}

    /**
     * Seleciona a rota habilitada de maior prioridade com binding compatível e bloqueia fallback
     * quando a tentativa anterior é incerta.
     *
     * @param routes rotas candidatas já conhecidas pelo serviço
     * @param eligibleTokenBindings pares provedor:afiliação compatíveis com o instrumento
     * @param previousAttemptUnknown indica se existe tentativa anterior com resultado ainda incerto
     * @return resultado definido pelo contrato; exceções interrompem a operação sem anunciar
     *     sucesso
     * @throws IllegalStateException quando a validação ou a capacidade exigida impedir a conclusão
     *     da operação
     */
    public Candidate choose(
            List<Candidate> routes,
            Set<String> eligibleTokenBindings,
            boolean previousAttemptUnknown) {
        if (previousAttemptUnknown) {
            throw new IllegalStateException("Resolve previous attempt before rerouting");
        }
        return routes.stream()
                .filter(Candidate::enabled)
                .filter(r -> eligibleTokenBindings.contains(r.provider() + ":" + r.affiliation()))
                .min(Comparator.comparingInt(Candidate::priority))
                .orElseThrow(() -> new IllegalStateException("No compatible route"));
    }
}
