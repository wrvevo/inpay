package br.com.includelabs.inpay.itau.domain;

/**
 * Pix Recebimentos segue a OpenAPI 2.41.15 publicada para o sandbox do Itaú. Boleto segue o cash
 * management v2. Rotas de Sispag só saem ao banco quando a confirmação de path está ligada.
 */
public enum ItauOperation {
    AUTH_TOKEN(ItauProduct.AUTHENTICATION, "POST", "", true),
    PIX_COB_POST(ItauProduct.PIX_RECEBIMENTOS, "POST", "/cob", true),
    PIX_COB_LIST(ItauProduct.PIX_RECEBIMENTOS, "GET", "/cob", true),
    PIX_COB_GET(ItauProduct.PIX_RECEBIMENTOS, "GET", "/cob/{txid}", true),
    PIX_COB_PUT(ItauProduct.PIX_RECEBIMENTOS, "PUT", "/cob/{txid}", true),
    PIX_COB_PATCH(ItauProduct.PIX_RECEBIMENTOS, "PATCH", "/cob/{txid}", true),
    PIX_COB_QRCODE(ItauProduct.PIX_RECEBIMENTOS, "GET", "/cob/{txid}/qrcode", true),
    PIX_LIST(ItauProduct.PIX_RECEBIMENTOS, "GET", "/pix", true),
    PIX_GET(ItauProduct.PIX_RECEBIMENTOS, "GET", "/pix/{e2eid}", true),
    PIX_REFUND_GET(ItauProduct.PIX_RECEBIMENTOS, "GET", "/pix/{e2eid}/devolucao/{id}", true),
    PIX_REFUND_PUT(ItauProduct.PIX_RECEBIMENTOS, "PUT", "/pix/{e2eid}/devolucao/{id}", true),
    PIX_WEBHOOK_LIST(ItauProduct.PIX_RECEBIMENTOS, "GET", "/webhook", true),
    PIX_WEBHOOK_GET(ItauProduct.PIX_RECEBIMENTOS, "GET", "/webhook/{chave}", true),
    PIX_WEBHOOK_PUT(ItauProduct.PIX_RECEBIMENTOS, "PUT", "/webhook/{chave}", true),
    PIX_WEBHOOK_DELETE(ItauProduct.PIX_RECEBIMENTOS, "DELETE", "/webhook/{chave}", true),
    PIX_LOTE_LIST(ItauProduct.PIX_RECEBIMENTOS, "GET", "/lotecobv", true),
    PIX_LOTE_PUT(ItauProduct.PIX_RECEBIMENTOS, "PUT", "/lotecobv/{id}", true),
    PIX_LOTE_PATCH(ItauProduct.PIX_RECEBIMENTOS, "PATCH", "/lotecobv/{id}", true),
    PIX_LOTE_GET(ItauProduct.PIX_RECEBIMENTOS, "GET", "/lotecobv/{id}", true),
    PIX_COBV_LIST(ItauProduct.PIX_RECEBIMENTOS, "GET", "/cobv", true),
    PIX_COBV_GET(ItauProduct.PIX_RECEBIMENTOS, "GET", "/cobv/{txid}", true),
    PIX_COBV_PUT(ItauProduct.PIX_RECEBIMENTOS, "PUT", "/cobv/{txid}", true),
    PIX_COBV_PATCH(ItauProduct.PIX_RECEBIMENTOS, "PATCH", "/cobv/{txid}", true),
    PIX_COBV_QRCODE(ItauProduct.PIX_RECEBIMENTOS, "GET", "/cobv/{txid}/qrcode", true),
    PIX_LOC_LIST(ItauProduct.PIX_RECEBIMENTOS, "GET", "/loc", true),
    PIX_LOC_POST(ItauProduct.PIX_RECEBIMENTOS, "POST", "/loc", true),
    PIX_LOC_GET(ItauProduct.PIX_RECEBIMENTOS, "GET", "/loc/{id}", true),
    PIX_LOC_UNLINK(ItauProduct.PIX_RECEBIMENTOS, "DELETE", "/loc/{id}/txid", true),
    BOLETO_CREATE(ItauProduct.BOLETO, "POST", "/boletos", true),
    BOLETO_LIST(ItauProduct.BOLETO, "GET", "/boletos", true),
    BOLETO_BAIXA(ItauProduct.BOLETO, "PATCH", "/boletos/{id_boleto}/baixa", true),
    SISPAG_TED_POST(ItauProduct.SISPAG, "POST", "/transferencias", false),
    SISPAG_PIX_POST(ItauProduct.SISPAG, "POST", "/pagamentos_pix", false),
    SISPAG_PIX_GET(ItauProduct.SISPAG, "GET", "/pagamentos_pix/{id}", false),
    SISPAG_PAYMENT_GET(ItauProduct.SISPAG, "GET", "/pagamentos/{id}", false);

    private final ItauProduct product;
    private final String method;
    private final String path;
    private final boolean documented;

    ItauOperation(ItauProduct product, String method, String path, boolean documented) {
        this.product = product;
        this.method = method;
        this.path = path;
        this.documented = documented;
    }

    public ItauProduct product() {
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

    public static ItauOperation fromName(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Unknown Itaú operation");
        }
        for (ItauOperation operation : values()) {
            if (operation.name().equalsIgnoreCase(value)) {
                return operation;
            }
        }
        throw new IllegalArgumentException("Unknown Itaú operation");
    }
}
