package br.com.includelabs.inpay.security;

import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;

/**
 * Componente de infraestrutura do InPay responsável por SecurityConfiguration. Não deve ser
 * referenciado pelo domínio.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfiguration {

    /**
     * Cria o usuário de desenvolvimento e seus escopos, exclusivamente no perfil local.
     *
     * @param password senha de desenvolvimento; utilizada somente no perfil local
     * @return resultado definido pelo contrato; exceções interrompem a operação sem anunciar
     *     sucesso
     */
    @Bean
    @Profile("local")
    UserDetailsService localUsers(@Value("${inpay.local-password}") String password) {
        var manager = new InMemoryUserDetailsManager();
        for (String username : new String[] {"inpay", "developer"}) {
            manager.createUser(
                    User.withUsername(username)
                            .password("{noop}" + password)
                            .authorities(SCOPES)
                            .build());
        }
        return manager;
    }

    private static final String[] SCOPES =
            new String[] {
                "SCOPE_accounts/read",
                "SCOPE_accounts/write",
                "SCOPE_accounts/transfer",
                "SCOPE_marketplace/read",
                "SCOPE_marketplace/write",
                "SCOPE_payments/create",
                "SCOPE_payments/read",
                "SCOPE_payments/capture",
                "SCOPE_payments/refund",
                "SCOPE_payments/void",
                "SCOPE_links/write",
                "SCOPE_links/read",
                "SCOPE_checkout/write",
                "SCOPE_checkout/read",
                "SCOPE_tokens/write",
                "SCOPE_tokens/read",
                "SCOPE_tokens/resolve",
                "SCOPE_fraud/evaluate",
                "SCOPE_fraud/read",
                "SCOPE_routing/select",
                "SCOPE_routing/read",
                "SCOPE_merchants/write",
                "SCOPE_merchants/read",
                "SCOPE_provider-events/ingest",
                "SCOPE_provider-events/read",
                "SCOPE_reconciliation/run",
                "SCOPE_reconciliation/read",
                "SCOPE_notifications/read",
                "SCOPE_transactions/read",
                "SCOPE_operations/read",
                "SCOPE_connectors-cielo/execute",
                "SCOPE_connectors-cielo/read",
                "SCOPE_connectors-rede/execute",
                "SCOPE_connectors-rede/read",
                "SCOPE_connectors-getnet/execute",
                "SCOPE_connectors-getnet/read",
                "SCOPE_connectors-stone/execute",
                "SCOPE_connectors-stone/read",
                "SCOPE_connectors-bin/execute",
                "SCOPE_connectors-bin/read",
                "SCOPE_connectors-global-payments/execute",
                "SCOPE_connectors-global-payments/read",
                "SCOPE_connectors-pagbank/execute",
                "SCOPE_connectors-pagbank/read",
                "SCOPE_connectors-safrapay/execute",
                "SCOPE_connectors-safrapay/read",
                "SCOPE_connectors-c6/execute",
                "SCOPE_connectors-c6/read",
                "SCOPE_connectors-itau/execute",
                "SCOPE_connectors-itau/read",
                "SCOPE_connectors-santander/execute",
                "SCOPE_connectors-santander/read",
                "SCOPE_connectors-bb/execute",
                "SCOPE_connectors-bb/read",
                "SCOPE_connectors-bradesco/execute",
                "SCOPE_connectors-bradesco/read",
                "SCOPE_connectors-caixa/execute",
                "SCOPE_connectors-caixa/read",
                "SCOPE_connectors-banrisul/execute",
                "SCOPE_connectors-banrisul/read",
                "SCOPE_connectors-clearsale/execute",
                "SCOPE_connectors-clearsale/read",
                "SCOPE_connectors-serasa-experian/execute",
                "SCOPE_connectors-serasa-experian/read",
                "SCOPE_painel/read",
                "SCOPE_painel/manage",
                "SCOPE_painel/admin",
                "SCOPE_homologation/write",
                "SCOPE_homologation/approve",
                "SCOPE_homologation/admin"
            };

    /**
     * Configura HTTP Basic local e permite abrir o Swagger sem liberar as APIs de negócio.
     *
     * @param http configurador ou cliente HTTP utilizado pelo adaptador
     * @return resultado definido pelo contrato; exceções interrompem a operação sem anunciar
     *     sucesso
     */
    @Bean
    @Profile("local")
    @Order(100)
    SecurityFilterChain local(HttpSecurity http, @Value("${inpay.local-password:}") String password)
            throws Exception {
        var chain = base(http, true).httpBasic(Customizer.withDefaults());
        if (!password.isBlank()) {
            chain.addFilterBefore(new LocalBearerFilter(password), BasicAuthenticationFilter.class);
        }
        return chain.build();
    }

    /**
     * Configura autenticação por access token Cognito e mantém a documentação protegida fora do
     * perfil local.
     *
     * @param http configurador ou cliente HTTP utilizado pelo adaptador
     * @return resultado definido pelo contrato; exceções interrompem a operação sem anunciar
     *     sucesso
     */
    @Bean
    @Profile("!local")
    @Order(100)
    SecurityFilterChain aws(HttpSecurity http) throws Exception {
        return base(http, false)
                .oauth2ResourceServer(o -> o.jwt(Customizer.withDefaults()))
                .build();
    }

    /**
     * Aplica regras comuns de segurança stateless, documentação, saúde e métricas.
     *
     * @param http configurador ou cliente HTTP utilizado pelo adaptador
     * @param localDocumentation habilita acesso local à documentação sem liberar endpoints de
     *     negócio
     * @return resultado definido pelo contrato; exceções interrompem a operação sem anunciar
     *     sucesso
     */
    private HttpSecurity base(HttpSecurity http, boolean localDocumentation) throws Exception {
        return // Bearer/Basic API only, no browser cookie session.
        http.csrf(c -> c.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(
                        a -> {
                            if (localDocumentation) {
                                a.requestMatchers(
                                                "/swagger-ui.html",
                                                "/swagger-ui/**",
                                                "/v3/api-docs",
                                                "/v3/api-docs/**",
                                                "/v3/api-docs.yaml")
                                        .permitAll();
                            } else {
                                a.requestMatchers(
                                                "/swagger-ui.html",
                                                "/swagger-ui/**",
                                                "/v3/api-docs",
                                                "/v3/api-docs/**",
                                                "/v3/api-docs.yaml")
                                        .hasAuthority("SCOPE_operations/read");
                            }
                            a.requestMatchers("/actuator/health", "/actuator/health/**")
                                    .permitAll()
                                    .requestMatchers("/actuator/prometheus")
                                    .hasAuthority("SCOPE_operations/read")
                                    .requestMatchers("/actuator/**")
                                    .denyAll()
                                    .anyRequest()
                                    .authenticated();
                        });
    }

    /**
     * Configura validação RS256/JWKS, emissor, expiração, client ID e audiência opcional.
     *
     * @param issuer emissor Cognito esperado, incluindo região e user pool
     * @param clients lista explícita de client IDs autorizados para esta API
     * @param audience audiência exigida quando houver resource binding; vazia quando não
     *     configurada
     * @return resultado definido pelo contrato; exceções interrompem a operação sem anunciar
     *     sucesso
     * @throws IllegalStateException quando a validação ou a capacidade exigida impedir a conclusão
     *     da operação
     */
    @Bean
    @Profile("!local")
    JwtDecoder jwtDecoder(
            @Value("${inpay.security.issuer}") String issuer,
            @Value("${inpay.security.allowed-clients}") String clients,
            @Value("${inpay.security.audience:}") String audience) {
        if (!issuer.startsWith("https://cognito-idp.") || clients.isBlank()) {
            throw new IllegalStateException(
                    "Configure Cognito issuer and explicit allowed client IDs");
        }
        NimbusJwtDecoder decoder =
                NimbusJwtDecoder.withJwkSetUri(issuer + "/.well-known/jwks.json")
                        .jwsAlgorithm(SignatureAlgorithm.RS256)
                        .build();
        decoder.setJwtValidator(
                new DelegatingOAuth2TokenValidator<>(
                        JwtValidators.createDefaultWithIssuer(issuer),
                        new CognitoAccessValidator(csv(clients), audience)));
        return decoder;
    }

    /**
     * Normaliza a lista de identidades configuradas, ignorando entradas vazias.
     *
     * @param value valor monetário em centavos; nunca utilizar ponto flutuante
     * @return resultado definido pelo contrato; exceções interrompem a operação sem anunciar
     *     sucesso
     */
    static Set<String> csv(String value) {
        Set<String> values = new HashSet<>();
        for (String item : value.split(",")) {
            if (!item.isBlank()) {
                values.add(item.trim());
            }
        }
        return Set.copyOf(values);
    }
}
