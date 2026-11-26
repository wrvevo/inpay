package br.com.includelabs.inpay.santander.domain.port;

import br.com.includelabs.inpay.santander.domain.SantanderOperation;
import br.com.includelabs.inpay.santander.domain.SantanderReply;
import java.util.Map;

/** Porta de saída. O domínio não conhece HTTP. */
public interface SantanderBankPort {
    SantanderReply send(
            SantanderOperation operation, String path, Map<String, Object> body, boolean form);
}
