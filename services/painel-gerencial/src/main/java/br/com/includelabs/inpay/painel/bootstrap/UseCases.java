package br.com.includelabs.inpay.painel.bootstrap;

import br.com.includelabs.inpay.painel.domain.Directory;
import br.com.includelabs.inpay.painel.domain.HomologationDesk;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@Profile("local")
public class UseCases {
    @Bean
    Directory directory(@Value("${inpay.local-password:inpay@123}") String secret) {
        return new Directory(secret);
    }

    @Bean
    HomologationDesk homologationDesk() {
        return new HomologationDesk();
    }

    /** Login, recuperação e redefinição ficam fora da autenticação. O restante exige token. */
    @Bean
    @Order(0)
    SecurityFilterChain openAuth(HttpSecurity http) throws Exception {
        return http.securityMatcher("/auth/login", "/auth/recover", "/auth/reset")
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .build();
    }
}
