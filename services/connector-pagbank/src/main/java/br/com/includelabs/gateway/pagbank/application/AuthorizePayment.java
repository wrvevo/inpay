package br.com.includelabs.gateway.pagbank.application;

import br.com.includelabs.gateway.pagbank.domain.Authorization;
import br.com.includelabs.gateway.pagbank.domain.port.AcquirerPort;
public final class AuthorizePayment {
private final AcquirerPort port;public AuthorizePayment(AcquirerPort port){this.port=port;}
public Authorization execute(Authorization request){
 if(request.amount()<=0 || request.operationId()==null || request.instrumentToken()==null)throw new IllegalArgumentException();
 return port.authorize(request);
}
}
