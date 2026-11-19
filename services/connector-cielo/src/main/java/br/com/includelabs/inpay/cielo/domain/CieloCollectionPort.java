package br.com.includelabs.inpay.cielo.domain;

/** Porta de tradução dos contratos específicos Cielo, independente de Spring e HTTP client. */
public interface CieloCollectionPort {
    /** Valida parâmetros e traduz uma operação InPay para o contrato nativo da coleção. */
    CieloRequestPlan prepare(
            CieloOperation operation, String resourceId, Long amount, Object payload);
}
