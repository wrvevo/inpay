package br.com.includelabs.inpay.bank;

/** Interpreta o header X-Mock da API do gateway e do conector. */
public enum MockMode {
    MOCK,
    LIVE;

    public static MockMode parse(String header, boolean mockEnabled) {
        String value = header == null ? "" : header.trim();
        if (value.isEmpty() || "false".equalsIgnoreCase(value)) {
            return LIVE;
        }
        if ("true".equalsIgnoreCase(value)) {
            if (!mockEnabled) {
                throw new IllegalArgumentException("Mock mode is disabled");
            }
            return MOCK;
        }
        throw new IllegalArgumentException("X-Mock must be true or false");
    }
}
