package br.com.includelabs.gateway.lambda.reconciliationtrigger;

import java.util.Map;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import com.amazonaws.services.lambda.runtime.*;
import br.com.includelabs.gateway.security.M2mTokenClient;
/** EventBridge trigger; ignores event-supplied URLs and uses one deployment-owned endpoint. */
public class Handler implements RequestHandler<Map<String,Object>,Map<String,Object>> {
 private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
 private final M2mTokenClient tokens=new M2mTokenClient(URI.create(required("TOKEN_ENDPOINT")),required("CLIENT_ID"),required("CLIENT_SECRET"));
 public Map<String,Object> handleRequest(Map<String,Object> event,Context context) {
  URI endpoint=URI.create(required("TARGET_ENDPOINT"));
  if(!"https".equals(endpoint.getScheme()))throw new IllegalStateException("HTTPS required");
  try {
   var req=HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(10))
    .header("Authorization","Bearer "+tokens.token(required("TARGET_SCOPE")))
    .header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString("{}")).build();
   var response=http.send(req,HttpResponse.BodyHandlers.discarding());
   if(response.statusCode()<200||response.statusCode()>=300)throw new IllegalStateException("Job not accepted");
   return Map.of("status","accepted");
  }catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException("Interrupted");}
   catch(java.io.IOException e){throw new IllegalStateException("Target unavailable");}
 }
 private static String required(String name){String v=System.getenv(name);if(v==null||v.isBlank())throw new IllegalStateException("Missing "+name);return v;}
}
