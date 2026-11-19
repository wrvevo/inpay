package br.com.includelabs.inpay.connector;

import br.com.includelabs.inpay.connector.domain.ProviderPort;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

/** Composição do runtime; os contratos HTTP específicos permanecem dentro de cada connector. */
@Configuration
@EnableConfigurationProperties(ProviderSettings.class)
public class ProviderConfiguration {
    /** Cria transporte com TLS e timeouts configurados por serviço. */
    @Bean
    ProviderTransport providerTransport(ProviderSettings settings) {
        return new ProviderTransport(settings);
    }

    /** Cria o cache de credenciais exclusivo da instância/estabelecimento. */
    @Bean
    ProviderAuthentication providerAuthentication(
            ProviderSettings settings, ProviderTransport transport) {
        return new ProviderAuthentication(settings, transport);
    }

    /** Usa a base PostgreSQL exclusiva do connector. */
    @Bean
    OperationJournal operationJournal(JdbcTemplate jdbc, PlatformTransactionManager manager) {
        return new JdbcOperationJournal(jdbc, manager);
    }

    /** Conecta o caso de uso aos adaptadores HTTP e PostgreSQL. */
    @Bean
    ProviderService providerService(
            ProviderSettings settings,
            ProviderPort port,
            ProviderTransport transport,
            ProviderAuthentication authentication,
            OperationJournal journal) {
        return new ProviderService(settings, port, transport, authentication, journal);
    }
}
