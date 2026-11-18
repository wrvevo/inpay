package br.com.includelabs.gateway.lambda.webhookforwarder;

import java.util.Map;
import com.amazonaws.services.lambda.runtime.*;
/** Deliberately fails closed until provider verification and durable ingress are configured. */
public class Handler implements RequestHandler<Map<String,Object>,Map<String,Object>> {
 public Map<String,Object> handleRequest(Map<String,Object> event,Context context) {
  return Map.of("statusCode",501,"body","{\"code\":\"PROVIDER_VERIFICATION_NOT_IMPLEMENTED\"}");
 }
}
