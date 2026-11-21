package br.com.includelabs.inpay.payment.bootstrap;

import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.*;

/** Separa contrato público atual e APIs legadas sem remover rotas em uso. */
@Configuration
public class GatewayDocumentation {
    /** Contratos orientados a clientes, pedidos, cobranças e provedores. */
    @Bean
    public GroupedOpenApi gatewayApi() {
        return GroupedOpenApi.builder().group("gateway-v1").pathsToMatch("/gateway/v1/**").build();
    }

    /** Compatibilidade com integrações existentes de pagamentos. */
    @Bean
    public GroupedOpenApi legacyApi() {
        return GroupedOpenApi.builder().group("legacy-payments").pathsToMatch("/v1/**").build();
    }
}
