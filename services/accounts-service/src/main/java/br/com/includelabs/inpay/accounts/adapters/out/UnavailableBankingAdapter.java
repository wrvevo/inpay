package br.com.includelabs.inpay.accounts.adapters.out;

import br.com.includelabs.inpay.accounts.domain.BankingPort;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** Bloqueia operação até existir parceiro contratado com autorização e ownership verificáveis. */
@Component
public class UnavailableBankingAdapter implements BankingPort {
    /** Nunca cria conta, saldo ou comprovante fictício, inclusive no perfil local. */
    public Map<String, Object> execute(
            UUID tenant, Operation operation, String resource, String key, Object request) {
        throw new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE, "BANKING_PARTNER_NOT_CONFIGURED");
    }
}
