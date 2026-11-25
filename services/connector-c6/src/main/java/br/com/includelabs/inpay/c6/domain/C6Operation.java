package br.com.includelabs.inpay.c6.domain;

/**
 * Operações do portal C6. As marcadas como documentadas seguem os paths publicados (auth, boleto
 * v1, Pix BACEN v2, bolepix v2, checkout, extrato, webhooks e C6 Pay). Agendamento, saldo isolado
 * e tokenização só saem ao banco quando a confirmação de path está ligada.
 */
public enum C6Operation {
    AUTH_TOKEN(C6Product.AUTHENTICATION, "POST", "/v1/auth", true),
    DDA_LIST(C6Product.SCHEDULE, "GET", "/v1/dda/bank_slips", false),
    SCHEDULE_BOLETO_CREATE(C6Product.SCHEDULE, "POST", "/v1/schedule_payments/bank_slips", false),
    SCHEDULE_PIX_CREATE(C6Product.SCHEDULE, "POST", "/v1/schedule_payments/pix", false),
    SCHEDULE_GET(C6Product.SCHEDULE, "GET", "/v1/schedule_payments/{id}", false),
    SCHEDULE_CANCEL(C6Product.SCHEDULE, "DELETE", "/v1/schedule_payments/{id}", false),
    BOLETO_CREATE(C6Product.BANK_SLIP, "POST", "/v1/bank_slips/", true),
    BOLETO_GET(C6Product.BANK_SLIP, "GET", "/v1/bank_slips/{id}", true),
    BOLETO_UPDATE(C6Product.BANK_SLIP, "PUT", "/v1/bank_slips/{id}", true),
    BOLETO_CANCEL(C6Product.BANK_SLIP, "PUT", "/v1/bank_slips/{id}/cancel", true),
    BOLEPIX_CREATE(C6Product.BOLEPIX, "POST", "/v2/bank_slips/", true),
    BOLEPIX_GET(C6Product.BOLEPIX, "GET", "/v2/bank_slips/{id}", true),
    BOLEPIX_CANCEL(C6Product.BOLEPIX, "PUT", "/v2/bank_slips/{id}/cancel", true),
    CHECKOUT_CREATE(C6Product.CHECKOUT, "POST", "/v1/checkouts/", true),
    CHECKOUT_GET(C6Product.CHECKOUT, "GET", "/v1/checkouts/{id}", true),
    CHECKOUT_CANCEL(C6Product.CHECKOUT, "PUT", "/v1/checkouts/{id}/cancel", true),
    CHECKOUT_TOKENIZE(C6Product.CHECKOUT, "POST", "/v1/cards/tokens", false),
    CHECKOUT_RECURRENCE_CREATE(C6Product.CHECKOUT, "POST", "/v1/checkouts/recurrences", false),
    CHECKOUT_RECURRENCE_GET(C6Product.CHECKOUT, "GET", "/v1/checkouts/recurrences/{id}", false),
    STATEMENT_LIST(C6Product.STATEMENT, "GET", "/v1/statement/", true),
    STATEMENT_GET(C6Product.STATEMENT, "GET", "/v1/statement/{id}", true),
    BALANCE_GET(C6Product.STATEMENT, "GET", "/v1/balance/", false),
    PIX_COB_POST(C6Product.PIX, "POST", "/v2/pix/cob", true),
    PIX_COB_PUT(C6Product.PIX, "PUT", "/v2/pix/cob/{txid}", true),
    PIX_COB_PATCH(C6Product.PIX, "PATCH", "/v2/pix/cob/{txid}", true),
    PIX_COB_GET(C6Product.PIX, "GET", "/v2/pix/cob/{txid}", true),
    PIX_COB_LIST(C6Product.PIX, "GET", "/v2/pix/cob", true),
    PIX_COBV_PUT(C6Product.PIX, "PUT", "/v2/pix/cobv/{txid}", true),
    PIX_COBV_PATCH(C6Product.PIX, "PATCH", "/v2/pix/cobv/{txid}", true),
    PIX_COBV_GET(C6Product.PIX, "GET", "/v2/pix/cobv/{txid}", true),
    PIX_COBV_LIST(C6Product.PIX, "GET", "/v2/pix/cobv", true),
    PIX_LOTE_PUT(C6Product.PIX, "PUT", "/v2/pix/lotecobv/{id}", true),
    PIX_LOTE_PATCH(C6Product.PIX, "PATCH", "/v2/pix/lotecobv/{id}", true),
    PIX_LOTE_GET(C6Product.PIX, "GET", "/v2/pix/lotecobv/{id}", true),
    PIX_GET(C6Product.PIX, "GET", "/v2/pix/pix/{e2eid}", true),
    PIX_LIST(C6Product.PIX, "GET", "/v2/pix/pix", true),
    PIX_REFUND_PUT(C6Product.PIX, "PUT", "/v2/pix/pix/{e2eid}/devolucao/{id}", true),
    PIX_REFUND_GET(C6Product.PIX, "GET", "/v2/pix/pix/{e2eid}/devolucao/{id}", true),
    PIX_WEBHOOK_PUT(C6Product.PIX, "PUT", "/v2/pix/webhook/{chave}", true),
    PIX_WEBHOOK_GET(C6Product.PIX, "GET", "/v2/pix/webhook/{chave}", true),
    PIX_WEBHOOK_DELETE(C6Product.PIX, "DELETE", "/v2/pix/webhook/{chave}", true),
    PIX_AUTO_REC_PUT(C6Product.PIX_AUTOMATIC, "PUT", "/v2/pix/rec/{id}", true),
    PIX_AUTO_REC_GET(C6Product.PIX_AUTOMATIC, "GET", "/v2/pix/rec/{id}", true),
    PIX_AUTO_REC_LIST(C6Product.PIX_AUTOMATIC, "GET", "/v2/pix/rec", true),
    PIX_AUTO_SOLIC_POST(C6Product.PIX_AUTOMATIC, "POST", "/v2/pix/solicrec", true),
    PIX_AUTO_SOLIC_GET(C6Product.PIX_AUTOMATIC, "GET", "/v2/pix/solicrec/{id}", true),
    PIX_AUTO_COBR_PUT(C6Product.PIX_AUTOMATIC, "PUT", "/v2/pix/cobr/{txid}", true),
    PIX_AUTO_COBR_GET(C6Product.PIX_AUTOMATIC, "GET", "/v2/pix/cobr/{txid}", true),
    PIX_AUTO_COBR_PATCH(C6Product.PIX_AUTOMATIC, "PATCH", "/v2/pix/cobr/{txid}", true),
    PIX_AUTO_RETRY(C6Product.PIX_AUTOMATIC, "POST", "/v2/pix/cobr/{txid}/retentativa/{data}", true),
    PIX_AUTO_WEBHOOK_REC_PUT(C6Product.PIX_AUTOMATIC, "PUT", "/v2/pix/webhookrec", true),
    PIX_AUTO_WEBHOOK_COBR_PUT(C6Product.PIX_AUTOMATIC, "PUT", "/v2/pix/webhookcobr", true),
    WEBHOOK_CREATE(C6Product.NOTIFICATIONS, "POST", "/v1/webhooks/", true),
    WEBHOOK_LIST(C6Product.NOTIFICATIONS, "GET", "/v1/webhooks/", true),
    WEBHOOK_GET(C6Product.NOTIFICATIONS, "GET", "/v1/webhooks/{id}", true),
    WEBHOOK_DELETE(C6Product.NOTIFICATIONS, "DELETE", "/v1/webhooks/{id}", true),
    TRANSACTIONS_LIST(C6Product.RECEIVABLES, "GET", "/v1/c6pay/statement/transactions", true),
    RECEIVABLES_LIST(C6Product.RECEIVABLES, "GET", "/v1/c6pay/statement/receivables", true);

    private final C6Product product;
    private final String method;
    private final String path;
    private final boolean documented;

    C6Operation(C6Product product, String method, String path, boolean documented) {
        this.product = product;
        this.method = method;
        this.path = path;
        this.documented = documented;
    }

    public C6Product product() {
        return product;
    }

    public String method() {
        return method;
    }

    public String path() {
        return path;
    }

    public boolean documented() {
        return documented;
    }

    public boolean hasBody() {
        return "POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method);
    }

    public static C6Operation fromName(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Unknown C6 operation");
        }
        for (C6Operation operation : values()) {
            if (operation.name().equalsIgnoreCase(value)) {
                return operation;
            }
        }
        throw new IllegalArgumentException("Unknown C6 operation");
    }
}
