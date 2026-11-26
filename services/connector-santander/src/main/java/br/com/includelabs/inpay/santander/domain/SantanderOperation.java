package br.com.includelabs.inpay.santander.domain;

/**
 * Pix segue o guia de recebimentos do portal (base /api/v1). Cobrança segue o guia collection bill
 * management v2. Pagamentos ficam em mock até o path contratado ser confirmado.
 */
public enum SantanderOperation {
    AUTH_TOKEN(SantanderProduct.AUTHENTICATION, "POST", "", true),
    PIX_COB_POST(SantanderProduct.PIX, "POST", "/cob", true),
    PIX_COB_LIST(SantanderProduct.PIX, "GET", "/cob", true),
    PIX_COB_GET(SantanderProduct.PIX, "GET", "/cob/{txid}", true),
    PIX_COB_PUT(SantanderProduct.PIX, "PUT", "/cob/{txid}", true),
    PIX_COB_PATCH(SantanderProduct.PIX, "PATCH", "/cob/{txid}", true),
    PIX_COBV_LIST(SantanderProduct.PIX, "GET", "/cobv", true),
    PIX_COBV_GET(SantanderProduct.PIX, "GET", "/cobv/{txid}", true),
    PIX_COBV_PUT(SantanderProduct.PIX, "PUT", "/cobv/{txid}", true),
    PIX_COBV_PATCH(SantanderProduct.PIX, "PATCH", "/cobv/{txid}", true),
    PIX_LOTE_PUT(SantanderProduct.PIX, "PUT", "/lotecobv/{id}", true),
    PIX_LOTE_PATCH(SantanderProduct.PIX, "PATCH", "/lotecobv/{id}", true),
    PIX_LOTE_GET(SantanderProduct.PIX, "GET", "/lotecobv/{id}", true),
    PIX_LIST(SantanderProduct.PIX, "GET", "/pix", true),
    PIX_GET(SantanderProduct.PIX, "GET", "/pix/{e2eid}", true),
    PIX_REFUND_PUT(SantanderProduct.PIX, "PUT", "/pix/{e2eid}/devolucao/{id}", true),
    PIX_REFUND_GET(SantanderProduct.PIX, "GET", "/pix/{e2eid}/devolucao/{id}", true),
    PIX_WEBHOOK_LIST(SantanderProduct.PIX, "GET", "/webhook", true),
    PIX_WEBHOOK_GET(SantanderProduct.PIX, "GET", "/webhook/{chave}", true),
    PIX_WEBHOOK_PUT(SantanderProduct.PIX, "PUT", "/webhook/{chave}", true),
    PIX_WEBHOOK_DELETE(SantanderProduct.PIX, "DELETE", "/webhook/{chave}", true),
    PIX_LOC_LIST(SantanderProduct.PIX, "GET", "/loc", true),
    PIX_LOC_POST(SantanderProduct.PIX, "POST", "/loc", true),
    PIX_LOC_GET(SantanderProduct.PIX, "GET", "/loc/{id}", true),
    PIX_LOC_UNLINK(SantanderProduct.PIX, "DELETE", "/loc/{id}/txid", true),
    WORKSPACE_CREATE(SantanderProduct.BILLING, "POST", "/workspaces", true),
    WORKSPACE_LIST(SantanderProduct.BILLING, "GET", "/workspaces", true),
    WORKSPACE_GET(SantanderProduct.BILLING, "GET", "/workspaces/{workspaceId}", true),
    WORKSPACE_UPDATE(SantanderProduct.BILLING, "PATCH", "/workspaces/{workspaceId}", true),
    WORKSPACE_DELETE(SantanderProduct.BILLING, "DELETE", "/workspaces/{workspaceId}", true),
    BANK_SLIP_CREATE(
            SantanderProduct.BILLING, "POST", "/workspaces/{workspaceId}/bank_slips", true),
    BANK_SLIP_UPDATE(
            SantanderProduct.BILLING, "PATCH", "/workspaces/{workspaceId}/bank_slips", true),
    BANK_SLIP_GET(
            SantanderProduct.BILLING,
            "GET",
            "/workspaces/{workspaceId}/bank_slips/{bankSlipId}",
            true),
    BILL_LIST(SantanderProduct.BILLING, "GET", "/bills", true),
    BILL_GET(SantanderProduct.BILLING, "GET", "/bills/{billId}", true),
    PAYMENT_WORKSPACE_CREATE(SantanderProduct.PAYMENTS, "POST", "/workspaces", false),
    PAYMENT_CREATE(SantanderProduct.PAYMENTS, "POST", "/workspaces/{workspaceId}/payments", false),
    PAYMENT_GET(SantanderProduct.PAYMENTS, "GET", "/workspaces/{workspaceId}/payments/{id}", false);

    private final SantanderProduct product;
    private final String method;
    private final String path;
    private final boolean documented;

    SantanderOperation(SantanderProduct product, String method, String path, boolean documented) {
        this.product = product;
        this.method = method;
        this.path = path;
        this.documented = documented;
    }

    public SantanderProduct product() {
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

    public static SantanderOperation fromName(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Unknown Santander operation");
        }
        for (SantanderOperation operation : values()) {
            if (operation.name().equalsIgnoreCase(value)) {
                return operation;
            }
        }
        throw new IllegalArgumentException("Unknown Santander operation");
    }
}
