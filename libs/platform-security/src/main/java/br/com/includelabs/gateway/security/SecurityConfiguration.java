package br.com.includelabs.gateway.security;

import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
@Configuration
@EnableMethodSecurity
public class SecurityConfiguration {
 @Bean @Profile("local")
 UserDetailsService localUsers(@Value("${gateway.local-password}") String password) {
  return new InMemoryUserDetailsManager(User.withUsername("developer")
    .password("{noop}"+password).authorities(SCOPES).build());
 }
 private static final String[] SCOPES = new String[]{"SCOPE_payments/create","SCOPE_payments/read","SCOPE_payments/capture","SCOPE_payments/refund","SCOPE_payments/void","SCOPE_links/write","SCOPE_links/read","SCOPE_checkout/write","SCOPE_checkout/read","SCOPE_tokens/write","SCOPE_tokens/read","SCOPE_tokens/resolve","SCOPE_fraud/evaluate","SCOPE_fraud/read","SCOPE_routing/select","SCOPE_routing/read","SCOPE_merchants/write","SCOPE_merchants/read","SCOPE_provider-events/ingest","SCOPE_provider-events/read","SCOPE_reconciliation/run","SCOPE_reconciliation/read","SCOPE_notifications/read","SCOPE_transactions/read","SCOPE_operations/read","SCOPE_connectors-cielo/execute","SCOPE_connectors-cielo/read","SCOPE_connectors-rede/execute","SCOPE_connectors-rede/read","SCOPE_connectors-getnet/execute","SCOPE_connectors-getnet/read","SCOPE_connectors-stone/execute","SCOPE_connectors-stone/read","SCOPE_connectors-bin/execute","SCOPE_connectors-bin/read","SCOPE_connectors-global-payments/execute","SCOPE_connectors-global-payments/read","SCOPE_connectors-pagbank/execute","SCOPE_connectors-pagbank/read","SCOPE_connectors-safrapay/execute","SCOPE_connectors-safrapay/read"};
 @Bean @Profile("local")
 SecurityFilterChain local(HttpSecurity http) throws Exception {
  return base(http).httpBasic(Customizer.withDefaults()).build();
 }
 @Bean @Profile("!local")
 SecurityFilterChain aws(HttpSecurity http) throws Exception {
  return base(http).oauth2ResourceServer(o -> o.jwt(Customizer.withDefaults())).build();
 }
 private HttpSecurity base(HttpSecurity http) throws Exception {
  return http.csrf(c -> c.disable()) // Bearer/Basic API only, no browser cookie session.
   .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
   .authorizeHttpRequests(a -> a.requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
    .requestMatchers("/actuator/prometheus").hasAuthority("SCOPE_operations/read")
    .requestMatchers("/actuator/**").denyAll().anyRequest().authenticated());
 }
 @Bean @Profile("!local")
 JwtDecoder jwtDecoder(@Value("${gateway.security.issuer}") String issuer,
    @Value("${gateway.security.allowed-clients}") String clients,
    @Value("${gateway.security.audience:}") String audience) {
  if (!issuer.startsWith("https://cognito-idp.") || clients.isBlank())
    throw new IllegalStateException("Configure Cognito issuer and explicit allowed client IDs");
  NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(issuer+"/.well-known/jwks.json")
    .jwsAlgorithm(SignatureAlgorithm.RS256).build();
  decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
    JwtValidators.createDefaultWithIssuer(issuer), new CognitoAccessValidator(csv(clients), audience)));
  return decoder;
 }
 static Set<String> csv(String value) {
  Set<String> values = new HashSet<>();
  for (String item : value.split(",")) if (!item.isBlank()) values.add(item.trim());
  return Set.copyOf(values);
 }
}
