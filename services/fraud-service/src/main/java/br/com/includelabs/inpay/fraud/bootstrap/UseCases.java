package br.com.includelabs.inpay.fraud.bootstrap;

import br.com.includelabs.inpay.fraud.adapters.out.http.LocalPartnerRiskAdapter;
import br.com.includelabs.inpay.fraud.application.AssessRisk;
import br.com.includelabs.inpay.fraud.domain.InPayRiskEngine;
import br.com.includelabs.inpay.fraud.domain.RiskPolicy;
import br.com.includelabs.inpay.fraud.domain.port.AssessmentStore;
import br.com.includelabs.inpay.fraud.domain.port.ExternalRiskPort;
import br.com.includelabs.inpay.fraud.domain.port.FraudModeSource;
import br.com.includelabs.inpay.fraud.domain.port.LearningStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.*;

/**
 * Composição dos casos de uso com suas portas de saída. A configuração Spring fica fora do domínio.
 */
@Configuration
public class UseCases {

    /**
     * Conecta o caso de uso à sua porta de saída, mantendo a composição Spring fora do domínio.
     *
     * @param store porta de persistência pertencente ao módulo
     * @return resultado definido pelo contrato; exceções interrompem a operação sem anunciar
     *     sucesso
     */
    @Bean
    AssessRisk assessRisk(
            AssessmentStore store,
            LearningStore learning,
            FraudModeSource modes,
            ObjectProvider<ExternalRiskPort> partners) {
        return new AssessRisk(
                new InPayRiskEngine(new RiskPolicy()),
                store,
                learning,
                modes,
                partners.getIfAvailable(
                        () -> (mode, probe) -> LocalPartnerRiskAdapter.unavailable(mode)));
    }
}
