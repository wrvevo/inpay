package br.com.includelabs.gateway.safrapay.application;

import br.com.includelabs.gateway.safrapay.domain.Authorization;
import br.com.includelabs.gateway.safrapay.domain.port.AcquirerPort;
public final class AuthorizePayment {
private final AcquirerPort port;public AuthorizePayment(AcquirerPort port){this.port=port;}
public Authorization execute(Authorization request){
 if(request.amount()<=0 || request.operationId()==null || request.instrumentToken()==null)throw new IllegalArgumentException();
 return port.authorize(request);
}
}
