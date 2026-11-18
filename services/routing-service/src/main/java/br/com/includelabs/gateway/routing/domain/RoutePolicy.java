package br.com.includelabs.gateway.routing.domain;

import java.util.*;
public final class RoutePolicy {
 public record Candidate(String provider,String affiliation,boolean enabled,int priority){}
 public Candidate choose(List<Candidate> routes,Set<String> eligibleTokenBindings,boolean previousAttemptUnknown){
  if(previousAttemptUnknown)throw new IllegalStateException("Resolve previous attempt before rerouting");
  return routes.stream().filter(Candidate::enabled)
   .filter(r->eligibleTokenBindings.contains(r.provider()+":"+r.affiliation()))
   .min(Comparator.comparingInt(Candidate::priority)).orElseThrow(()->new IllegalStateException("No compatible route"));
 }
}
