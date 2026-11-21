package br.com.includelabs.inpay.openapi;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/** Configura o contrato OpenAPI de cada serviço sem introduzir dependências no domínio. */
@Configuration
public class InPayOpenApiConfiguration {

    /**
     * Publica metadados e o esquema de autenticação correspondente ao perfil ativo. Tokens Cognito
     * devem ser obtidos no backend; o Swagger não recebe client secrets.
     *
     * @param serviceName nome do serviço que está publicando a documentação
     * @param environment perfis ativos da aplicação
     * @return contrato OpenAPI complementado automaticamente pelos controllers
     */
    @Bean
    public OpenAPI inPayOpenApi(
            @Value("${spring.application.name}") String serviceName, Environment environment) {
        boolean localProfile = environment.matchesProfiles("local");
        String schemeName = localProfile ? "localBasic" : "cognitoBearer";
        SecurityScheme scheme = new SecurityScheme().type(SecurityScheme.Type.HTTP);
        if (localProfile) {
            scheme.scheme("basic")
                    .description("Somente desenvolvimento: developer e LOCAL_PASSWORD.");
        } else {
            scheme.scheme("bearer")
                    .bearerFormat("JWT")
                    .description(
                            "Access token Cognito. Cada operação informa o scope obrigatório.");
        }
        return new OpenAPI()
                .info(
                        new Info()
                                .title(
                                        switch (serviceName) {
                                            case "payment-core-service" -> "INPAY Gateway";
                                            case "accounts-service" -> "INPAY Accounts";
                                            case "marketplace-service" -> "INPAY Marketplace";
                                            case "credits-service" -> "INPAY Credits";
                                            default -> "InPay | " + serviceName;
                                        })
                                .version("0.2.0")
                                .description(
                                        "InPay: Gateway, Accounts, Marketplace e Credits. Valores em centavos, BRL, "
                                                + "identificadores opacos e isolamento por tenant. POSTs de criação usam Idempotency-Key, "
                                                + "exceto contratos Credits que usam operationId. 202 confirma registro, não liquidação. "
                                                + "Operações 501 não implementadas; 503 indica parceiro/configuração indisponível. "
                                                + "Conectores externos explicitamente habilitados podem chamar sandbox ou produção; "
                                                + "consulte a configuração e o contrato de cada operação."))
                .components(new Components().addSecuritySchemes(schemeName, scheme))
                .addSecurityItem(new SecurityRequirement().addList(schemeName));
    }
}
