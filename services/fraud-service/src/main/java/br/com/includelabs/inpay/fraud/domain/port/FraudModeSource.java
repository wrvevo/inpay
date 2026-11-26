package br.com.includelabs.inpay.fraud.domain.port;

/** Modo de antifraude ativo neste processo. */
public interface FraudModeSource {
    String current();

    String select(String mode);
}
