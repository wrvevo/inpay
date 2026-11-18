package br.com.includelabs.gateway.security;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import tools.jackson.databind.json.JsonMapper;
/** Explicit per-destination client. Secrets injected from Secrets Manager by the deployment. */
public final class M2mTokenClient {
 private final URI endpoint; private final String clientId; private final String secret;
 private final Map<String,Cached> cache=new ConcurrentHashMap<>();
 private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
 private final JsonMapper mapper=JsonMapper.builder().build();
 private record Cached(String token,Instant expiresAt){}
 public M2mTokenClient(URI endpoint,String clientId,String secret) {
  if(!"https".equals(endpoint.getScheme())) throw new IllegalArgumentException("HTTPS required");
  this.endpoint=endpoint;this.clientId=clientId;this.secret=secret;
 }
 public synchronized String token(String scopes) {
  Cached old=cache.get(scopes);
  if(old!=null && old.expiresAt().isAfter(Instant.now().plusSeconds(30))) return old.token();
  String credentials=Base64.getEncoder().encodeToString((clientId+":"+secret).getBytes(StandardCharsets.UTF_8));
  var request=HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(5))
   .header("Authorization","Basic "+credentials).header("Content-Type","application/x-www-form-urlencoded")
   .POST(HttpRequest.BodyPublishers.ofString("grant_type=client_credentials&scope="+
     URLEncoder.encode(scopes,StandardCharsets.UTF_8))).build();
  try {
   var response=http.send(request,HttpResponse.BodyHandlers.ofString());
   if(response.statusCode()!=200) throw new IllegalStateException("M2M token unavailable");
   var json=mapper.readTree(response.body());
   String value=json.path("access_token").asText();
   long ttl=json.path("expires_in").asLong();
   if(value.isBlank() || ttl<=30) throw new IllegalStateException("Invalid token response");
   cache.put(scopes,new Cached(value,Instant.now().plusSeconds(ttl)));return value;
  } catch(InterruptedException e) {Thread.currentThread().interrupt();throw new IllegalStateException("Token interrupted");}
    catch(java.io.IOException e) {throw new IllegalStateException("Token unavailable");}
 }
}
