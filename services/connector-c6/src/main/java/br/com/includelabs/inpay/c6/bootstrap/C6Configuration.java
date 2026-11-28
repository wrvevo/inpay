package br.com.includelabs.inpay.c6.bootstrap;

import br.com.includelabs.inpay.c6.adapters.out.http.C6HttpAdapter;
import br.com.includelabs.inpay.c6.application.C6ResultPublisher;
import br.com.includelabs.inpay.c6.application.ExecuteC6Operation;
import br.com.includelabs.inpay.c6.domain.port.C6BankPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration
@EnableConfigurationProperties(C6Settings.class)
public class C6Configuration {
    /** Instancia o adaptador HTTP que implementa a porta bancária. */
    @Bean
    C6BankPort c6BankPort(C6Settings settings) {
        return new C6HttpAdapter(settings);
    }

    /** Define o publicador padrão de resultados do conector. */
    @Bean
    @ConditionalOnMissingBean(C6ResultPublisher.class)
    C6ResultPublisher c6ResultPublisher() {
        return C6ResultPublisher.NONE;
    }

    /** Compõe o caso de uso com as portas e parâmetros configurados. */
    @Bean
    ExecuteC6Operation executeC6Operation(
            C6BankPort port, C6ResultPublisher results, C6Settings settings, Environment environment) {
        return new ExecuteC6Operation(
                port, results, settings.enabled() && environment.matchesProfiles("local & !aws"), settings.confirmUnverifiedPaths());
    }
}
