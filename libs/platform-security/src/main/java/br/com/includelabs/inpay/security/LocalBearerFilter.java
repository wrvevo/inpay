package br.com.includelabs.inpay.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/** Aceita o token local no perfil local. Sem o cabeçalho Bearer, o Basic continua valendo. */
public final class LocalBearerFilter extends OncePerRequestFilter {
    private final String secret;

    public LocalBearerFilter(String secret) {
        this.secret = secret;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            try {
                LocalApiToken.Claims claims =
                        LocalApiToken.verify(secret, header.substring(7).trim());
                List<SimpleGrantedAuthority> authorities = new ArrayList<>();
                authorities.add(new SimpleGrantedAuthority("ROLE_" + claims.role()));
                for (String scope : claims.scopes()) {
                    authorities.add(new SimpleGrantedAuthority("SCOPE_" + scope));
                }
                SecurityContextHolder.getContext()
                        .setAuthentication(
                                new UsernamePasswordAuthenticationToken(
                                        claims.subject(), null, authorities));
            } catch (RuntimeException exception) {
                response.setStatus(401);
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
