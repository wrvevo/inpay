package br.com.includelabs.gateway.token.domain.port;

import java.util.*;
import br.com.includelabs.gateway.token.domain.Instrument;
public interface InstrumentStore {void save(Instrument token);Optional<Instrument> find(UUID tenant,String token);void revoke(UUID tenant,String token);}
