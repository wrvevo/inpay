package br.com.includelabs.inpay.bank;

/** Instituição com conector próprio. A URL padrão não prova homologação nem credencial. */
public enum BankId {
    C6("c6", "C6 Bank", "https://developers.c6bank.com.br/apis", "https://baas-api-sandbox.c6bank.info", 8111),
    ITAU(
            "itau",
            "Itaú",
            "https://devportal.itau.com.br/",
            "https://sandbox.devportal.itau.com.br",
            8112),
    SANTANDER(
            "santander",
            "Santander",
            "https://developer.santander.com.br/",
            "https://trust-sandbox.api.santander.com.br",
            8113),
    BB("bb", "Banco do Brasil", "https://developers.bb.com.br/", "https://api.hm.bb.com.br", 8114),
    BRADESCO(
            "bradesco",
            "Bradesco",
            "https://api.bradesco/",
            "https://openapisandbox.prebanco.com.br",
            8115),
    CAIXA("caixa", "Caixa", "https://www.caixa.gov.br/", "https://api.caixa.gov.br", 8116),
    BANRISUL(
            "banrisul",
            "Banrisul",
            "https://developers.banrisul.com.br/",
            "https://api.banrisul.com.br",
            8117);

    private final String slug;
    private final String displayName;
    private final String documentation;
    private final String defaultSandbox;
    private final int port;

    BankId(String slug, String displayName, String documentation, String defaultSandbox, int port) {
        this.slug = slug;
        this.displayName = displayName;
        this.documentation = documentation;
        this.defaultSandbox = defaultSandbox;
        this.port = port;
    }

    public String slug() {
        return slug;
    }

    public String displayName() {
        return displayName;
    }

    public String documentation() {
        return documentation;
    }

    /** Host de sandbox publicado; substituir pela URL contratada antes de habilitar a chamada real. */
    public String defaultSandbox() {
        return defaultSandbox;
    }

    public int port() {
        return port;
    }

    public static BankId fromSlug(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Unknown bank");
        }
        for (BankId bank : values()) {
            if (bank.slug.equalsIgnoreCase(value) || bank.name().equalsIgnoreCase(value)) {
                return bank;
            }
        }
        throw new IllegalArgumentException("Unknown bank");
    }
}
