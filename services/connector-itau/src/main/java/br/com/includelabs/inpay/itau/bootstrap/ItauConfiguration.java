package br.com.includelabs.inpay.itau.bootstrap;

import br.com.includelabs.inpay.itau.adapters.out.http.ItauHttpAdapter;
import br.com.includelabs.inpay.itau.application.ExecuteItauOperation;
import br.com.includelabs.inpay.itau.domain.port.ItauBankPort;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration
@EnableConfigurationProperties(ItauSettings.class)
public class ItauConfiguration {
    @Bean
    ItauBankPort itauBankPort(ItauSettings settings) {
        return new ItauHttpAdapter(settings);
    }

    @Bean
    ExecuteItauOperation executeItauOperation(ItauBankPort port, ItauSettings settings, Environment environment) {
        return new ExecuteItauOperation(
                port, settings.enabled() && environment.matchesProfiles("local & !aws"), settings.confirmUnverifiedPaths());
    }
}
