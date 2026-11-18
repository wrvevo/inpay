package br.com.includelabs.gateway.links.domain.port;

import java.util.*;
import br.com.includelabs.gateway.links.domain.PaymentLink;
public interface LinkStore {void save(PaymentLink link);Optional<PaymentLink> find(UUID merchant,UUID id);void revoke(UUID merchant,UUID id);int expire();}
