package br.com.includelabs.gateway.security;

import java.util.*;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
@Component
public class TenantContext {
 private final Environment env; private final String bindings; private final Set<String> trusted;
 public TenantContext(Environment env, @Value("${gateway.security.client-tenants:}") String bindings,
   @Value("${gateway.security.trusted-services:}") String trusted) {
  this.env=env; this.bindings=bindings; this.trusted=SecurityConfiguration.csv(trusted);
 }
 public UUID resolve(String requested) {
  Authentication auth=SecurityContextHolder.getContext().getAuthentication();
  if(auth==null || !auth.isAuthenticated()) throw new AccessDeniedException("Authentication required");
  UUID tenant=null;
  if(env.matchesProfiles("local")) tenant=UUID.fromString(env.getRequiredProperty("gateway.local-merchant"));
  else if(auth instanceof JwtAuthenticationToken jwt) {
   String client=jwt.getToken().getClaimAsString("client_id");
   for(String binding:bindings.split(",")) {
    String[] parts=binding.trim().split("=",2);
    if(parts.length==2 && parts[0].equals(client)) tenant=UUID.fromString(parts[1]);
   }
   // Internal services may carry tenant context; destination still checks resource ownership.
   if(tenant==null && trusted.contains(client) && requested!=null) tenant=UUID.fromString(requested);
  }
  if(tenant==null || (requested!=null && !tenant.equals(UUID.fromString(requested))))
    throw new AccessDeniedException("Tenant not authorized");
  return tenant;
 }
}
