package br.com.includelabs.inpay.santander.bootstrap;

import br.com.includelabs.inpay.santander.adapters.out.http.SantanderHttpAdapter;
import br.com.includelabs.inpay.santander.application.ExecuteSantanderOperation;
import br.com.includelabs.inpay.santander.domain.port.SantanderBankPort;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration
@EnableConfigurationProperties(SantanderSettings.class)
public class SantanderConfiguration {
    @Bean
    SantanderBankPort santanderBankPort(SantanderSettings settings) {
        return new SantanderHttpAdapter(settings);
    }

    @Bean
    ExecuteSantanderOperation executeSantanderOperation(
            SantanderBankPort port, SantanderSettings settings, Environment environment) {
        return new ExecuteSantanderOperation(
                port, settings.enabled() && environment.matchesProfiles("local & !aws"), settings.confirmUnverifiedPaths());
    }
}
