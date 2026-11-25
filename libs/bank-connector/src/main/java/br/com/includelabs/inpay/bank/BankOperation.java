package br.com.includelabs.inpay.bank;

/**
 * Operações de pagamento expostas pelos bancos. Pix segue o contrato BACEN; boleto, TED, saldo e
 * chaves só saem para o adquirente quando o path contratado é confirmado.
 */
public enum BankOperation {
    OAUTH_TOKEN("POST", "/oauth/token", false),
    PIX_COB_PUT("PUT", "/pix/v2/cob/{txid}", true),
    PIX_COB_PATCH("PATCH", "/pix/v2/cob/{txid}", true),
    PIX_COB_GET("GET", "/pix/v2/cob/{txid}", true),
    PIX_COB_POST("POST", "/pix/v2/cob", true),
    PIX_COB_LIST("GET", "/pix/v2/cob", true),
    PIX_COBV_PUT("PUT", "/pix/v2/cobv/{txid}", true),
    PIX_COBV_PATCH("PATCH", "/pix/v2/cobv/{txid}", true),
    PIX_COBV_GET("GET", "/pix/v2/cobv/{txid}", true),
    PIX_COBV_LIST("GET", "/pix/v2/cobv", true),
    PIX_LOTECOBV_PUT("PUT", "/pix/v2/lotecobv/{id}", true),
    PIX_LOTECOBV_PATCH("PATCH", "/pix/v2/lotecobv/{id}", true),
    PIX_LOTECOBV_GET("GET", "/pix/v2/lotecobv/{id}", true),
    PIX_LOC_POST("POST", "/pix/v2/loc", true),
    PIX_LOC_LIST("GET", "/pix/v2/loc", true),
    PIX_LOC_GET("GET", "/pix/v2/loc/{id}", true),
    PIX_LOC_UNLINK("DELETE", "/pix/v2/loc/{id}/txid", true),
    PIX_WEBHOOK_PUT("PUT", "/pix/v2/webhook/{chave}", true),
    PIX_WEBHOOK_GET("GET", "/pix/v2/webhook/{chave}", true),
    PIX_WEBHOOK_DELETE("DELETE", "/pix/v2/webhook/{chave}", true),
    PIX_WEBHOOK_LIST("GET", "/pix/v2/webhook", true),
    PIX_GET("GET", "/pix/v2/pix/{e2eid}", true),
    PIX_LIST("GET", "/pix/v2/pix", true),
    PIX_REFUND_PUT("PUT", "/pix/v2/pix/{e2eid}/devolucao/{id}", true),
    PIX_REFUND_GET("GET", "/pix/v2/pix/{e2eid}/devolucao/{id}", true),
    BOLETO_REGISTER("POST", "/boletos/v1/boletos", false),
    BOLETO_GET("GET", "/boletos/v1/boletos/{id}", false),
    BOLETO_LIST("GET", "/boletos/v1/boletos", false),
    BOLETO_BAIXA("POST", "/boletos/v1/boletos/{id}/baixar", false),
    BOLETO_PDF("GET", "/boletos/v1/boletos/{id}/pdf", false),
    PIX_PAYMENT_POST("POST", "/pix/v2/pagamentos", false),
    PIX_PAYMENT_GET("GET", "/pix/v2/pagamentos/{id}", false),
    TED_POST("POST", "/ted/v1/transferencias", false),
    TED_GET("GET", "/ted/v1/transferencias/{id}", false),
    BALANCE_GET("GET", "/contas/v1/saldo", false),
    STATEMENT_GET("GET", "/contas/v1/extrato", false),
    PIX_KEY_POST("POST", "/pix/v2/chaves", false),
    PIX_KEY_GET("GET", "/pix/v2/chaves/{chave}", false),
    PIX_KEY_DELETE("DELETE", "/pix/v2/chaves/{chave}", false),
    PIX_KEY_LIST("GET", "/pix/v2/chaves", false);

    private final String method;
    private final String path;
    private final boolean pixStandard;

    BankOperation(String method, String path, boolean pixStandard) {
        this.method = method;
        this.path = path;
        this.pixStandard = pixStandard;
    }

    public String method() {
        return method;
    }

    public String path() {
        return path;
    }

    /** Contrato Pix do BACEN, comum aos bancos listados. */
    public boolean pixStandard() {
        return pixStandard;
    }

    public boolean hasBody() {
        return "POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method);
    }

    public String pathRegex() {
        return path.replaceAll("\\{[^}]+}", "[^/]+");
    }

    /** Menor número vence no WireMock; rotas com mais parâmetros são mais específicas. */
    public int priority() {
        int placeholders = path.split("\\{", -1).length - 1;
        return 20 - placeholders;
    }

    public static BankOperation fromName(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Unknown bank operation");
        }
        for (BankOperation operation : values()) {
            if (operation.name().equalsIgnoreCase(value)) {
                return operation;
            }
        }
        throw new IllegalArgumentException("Unknown bank operation");
    }
}
