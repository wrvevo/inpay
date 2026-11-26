package br.com.includelabs.inpay.fraud.domain.port;

import java.util.UUID;

/** Memória de chargebacks por dispositivo, usada para ajustar o score do InPay Risk. */
public interface LearningStore {
    record Counts(int confirmed, int legitimate) {}

    Counts get(UUID merchantId, String deviceKey);

    void record(UUID merchantId, String deviceKey, boolean confirmedFraud);
}
