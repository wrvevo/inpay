package br.com.includelabs.gateway.fraud.domain;

import java.util.*;
public final class RiskPolicy {
 public enum Decision { ACCEPT, REVIEW, REJECT }
 public record Signals(long amount,int attemptsLastHour,int declinesLastHour,boolean knownCompromised) {
  public Signals {if(amount<=0||attemptsLastHour<0||declinesLastHour<0)throw new IllegalArgumentException("Invalid signals");}
 }
 public record Assessment(int score,Decision decision,String policyVersion,List<String> reasons){}
 public Assessment evaluate(Signals signals) {
  int score=0;var reasons=new ArrayList<String>();
  if(signals.knownCompromised()){score+=70;reasons.add("KNOWN_COMPROMISED");}
  if(signals.attemptsLastHour()>=5){score+=30;reasons.add("VELOCITY");}
  if(signals.declinesLastHour()>=3){score+=25;reasons.add("RECENT_DECLINES");}
  if(signals.amount()>100000){score+=20;reasons.add("HIGH_AMOUNT");}
  score=Math.min(100,score);
  return new Assessment(score,score>=70?Decision.REJECT:score>=40?Decision.REVIEW:Decision.ACCEPT,
   "demo-rules-v1",List.copyOf(reasons));
 }
}
