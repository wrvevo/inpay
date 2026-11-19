package br.com.includelabs.inpay.connector;

/** Indica configuração incompleta ou autenticação indisponível antes do envio financeiro. */
public class ProviderUnavailableException extends RuntimeException {
    /** Mantém diagnóstico interno sem expor segredos. */
    public ProviderUnavailableException(String message) {
        super(message);
    }
}
