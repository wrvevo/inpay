package br.com.includelabs.inpay.itau.bootstrap;

import br.com.includelabs.inpay.itau.adapters.out.http.ItauHttpAdapter;
import br.com.includelabs.inpay.itau.application.ExecuteItauOperation;
import br.com.includelabs.inpay.itau.domain.port.ItauBankPort;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ItauSettings.class)
public class ItauConfiguration {
    @Bean
    ItauBankPort itauBankPort(ItauSettings settings) {
        return new ItauHttpAdapter(settings);
    }

    @Bean
    ExecuteItauOperation executeItauOperation(ItauBankPort port, ItauSettings settings) {
        return new ExecuteItauOperation(
                port, settings.enabled(), settings.confirmUnverifiedPaths());
    }
}
