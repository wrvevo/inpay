package br.com.includelabs.inpay.c6.bootstrap;

import br.com.includelabs.inpay.c6.adapters.out.http.C6HttpAdapter;
import br.com.includelabs.inpay.c6.application.C6ResultPublisher;
import br.com.includelabs.inpay.c6.application.ExecuteC6Operation;
import br.com.includelabs.inpay.c6.domain.port.C6BankPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(C6Settings.class)
public class C6Configuration {
    @Bean
    C6BankPort c6BankPort(C6Settings settings) {
        return new C6HttpAdapter(settings);
    }

    @Bean
    @ConditionalOnMissingBean(C6ResultPublisher.class)
    C6ResultPublisher c6ResultPublisher() {
        return C6ResultPublisher.NONE;
    }

    @Bean
    ExecuteC6Operation executeC6Operation(C6BankPort port, C6ResultPublisher results, C6Settings settings) {
        return new ExecuteC6Operation(
                port, results, settings.enabled(), settings.confirmUnverifiedPaths());
    }
}
