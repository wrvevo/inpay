package br.com.includelabs.inpay.c6.domain.port;

import br.com.includelabs.inpay.c6.domain.C6Operation;
import br.com.includelabs.inpay.c6.domain.C6Reply;
import java.util.Map;

/** Porta de saída para o sandbox/produção do C6. O domínio não conhece HTTP nem Kafka. */
public interface C6BankPort {
    C6Reply send(C6Operation operation, String path, Map<String, Object> body, boolean form);
}
