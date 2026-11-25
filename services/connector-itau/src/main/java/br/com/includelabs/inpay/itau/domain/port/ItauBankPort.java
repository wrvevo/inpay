package br.com.includelabs.inpay.itau.domain.port;

import br.com.includelabs.inpay.itau.domain.ItauOperation;
import br.com.includelabs.inpay.itau.domain.ItauReply;
import java.util.Map;

/** Porta de saída. O domínio não conhece HTTP. */
public interface ItauBankPort {
    ItauReply send(ItauOperation operation, String path, Map<String, Object> body, boolean form);
}
