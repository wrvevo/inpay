package br.com.includelabs.gateway.globalpayments.domain;

import java.util.UUID;
public record Authorization(UUID operationId,UUID merchantId,long amount,String instrumentToken,Outcome outcome) {
 public enum Outcome { AUTHORIZED, DECLINED, UNKNOWN, UNSUPPORTED }
}
