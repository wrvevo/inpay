package br.com.includelabs.inpay.bank;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@EnableConfigurationProperties(BankSettings.class)
public class BankConnectorConfiguration {
    @Bean
    BankTransport bankTransport(BankSettings settings, ObjectProvider<MeterRegistry> meters) {
        return new BankTransport(settings, meters.getIfAvailable());
    }

    @Bean
    BankConnector bankConnector(BankSettings settings, BankTransport transport) {
        return new BankConnector(settings, transport);
    }

    @Bean
    WebMvcConfigurer bankRequestLog() {
        return new WebMvcConfigurer() {
            @Override
            public void addInterceptors(InterceptorRegistry registry) {
                registry.addInterceptor(new BankRequestLog());
            }
        };
    }
}
