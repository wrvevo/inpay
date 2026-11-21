package br.com.includelabs.inpay.billing.adapters.in.rest;

import br.com.includelabs.inpay.billing.application.*;
import br.com.includelabs.inpay.billing.application.BillingCommands.Invoice;
import br.com.includelabs.inpay.billing.bootstrap.BillingIdentity;
import br.com.includelabs.inpay.billing.domain.*;
import br.com.includelabs.inpay.security.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.*;
import org.springframework.http.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** Fachada ERP com tenant obrigatório e a mesma idempotência do backoffice. */
@RestController
@RequestMapping("/billing/v1/erp")
@Tag(name = "ERP: REST e SOAP")
public class ErpController {
    private final BillingOperations operations;
    private final BillingStore store;
    private final BillingIdentity identity;
    private final TenantContext tenants;
    private final jakarta.validation.Validator validator;

    public ErpController(
            BillingOperations operations,
            BillingStore store,
            BillingIdentity identity,
            TenantContext tenants,
            jakarta.validation.Validator validator) {
        this.operations = operations;
        this.store = store;
        this.identity = identity;
        this.tenants = tenants;
        this.validator = validator;
    }

    /** REST recomendado para novos conectores SAP, Oracle, Dynamics e TOTVS. */
    @PostMapping("/invoices")
    @PreAuthorize("@billingIdentity.allowed('erp')")
    @Operation(
            summary = "Importar cobrança de ERP",
            description =
                    "billing/erp; client_id precisa estar vinculado ao tenant. Não oferece certificação específica de ERP.")
    public Map<String, Object> invoice(
            @RequestHeader(value = "X-Merchant-Id", required = false) String tenant,
            @Valid @RequestBody Invoice invoice) {
        if (!store.merchantTenant(invoice.merchantId()).equals(tenants.resolve(tenant)))
            throw new AccessDeniedException("Merchant not owned");
        return operations.createInvoice(identity.actor(), invoice);
    }

    /**
     * SOAP 1.1 mínimo interoperável; DTD, entidades externas e mensagens maiores que 64 KiB são
     * recusadas.
     */
    @PostMapping(value = "/soap", consumes = "text/xml", produces = "text/xml")
    @PreAuthorize("@billingIdentity.allowed('erp')")
    @Operation(
            summary = "Importar cobrança por SOAP 1.1",
            description =
                    "Operação CreateInvoice, namespace urn:inpay:billing:v1; condições financeiras zero neste contrato inicial.")
    public ResponseEntity<String> soap(
            @RequestHeader(value = "X-Merchant-Id", required = false) String tenant,
            @RequestBody String xml) {
        try {
            var body = parse(xml);
            Invoice invoice =
                    new Invoice(
                            UUID.fromString(body.get("operationId")),
                            UUID.fromString(body.get("merchantId")),
                            body.get("customerRef"),
                            Long.parseLong(body.get("amount")),
                            body.get("method"),
                            LocalDate.parse(body.get("dueDate")),
                            new BillingPolicy.Conditions(0, 0, 0, null, 0),
                            body.get("contractReference"));
            if (!validator.validate(invoice).isEmpty())
                throw new IllegalArgumentException("Invalid invoice");
            var result = invoice(tenant, invoice);
            return ResponseEntity.ok(
                    envelope(
                            "<CreateInvoiceResponse xmlns=\"urn:inpay:billing:v1\"><invoiceId>"
                                    + result.get("id")
                                    + "</invoiceId><status>OPEN</status></CreateInvoiceResponse>"));
        } catch (AccessDeniedException denied) {
            throw denied;
        } catch (Exception invalid) {
            return ResponseEntity.status(500)
                    .body(
                            envelope(
                                    "<soap:Fault><faultcode>soap:Client</faultcode><faultstring>Invalid or conflicting invoice command</faultstring></soap:Fault>"));
        }
    }

    /** Contrato WSDL estático, sem dependência de endpoints externos na publicação. */
    @GetMapping(value = "/billing.wsdl", produces = "text/xml")
    @PreAuthorize("@billingIdentity.allowed('erp')")
    public String wsdl() throws java.io.IOException {
        try (var input = getClass().getResourceAsStream("/erp/billing.wsdl")) {
            return new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    static Map<String, String> parse(String xml) throws Exception {
        if (xml.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 65536)
            throw new IllegalArgumentException();
        var factory = javax.xml.parsers.DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setAttribute(javax.xml.XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(javax.xml.XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        var builder = factory.newDocumentBuilder();
        builder.setErrorHandler(new org.xml.sax.helpers.DefaultHandler());
        var document =
                builder.parse(
                        new java.io.ByteArrayInputStream(
                                xml.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        var envelope = document.getDocumentElement();
        if (!"Envelope".equals(envelope.getLocalName())
                || !"http://schemas.xmlsoap.org/soap/envelope/".equals(envelope.getNamespaceURI()))
            throw new IllegalArgumentException();
        var bodies =
                envelope.getElementsByTagNameNS(
                        "http://schemas.xmlsoap.org/soap/envelope/", "Body");
        if (bodies.getLength() != 1) throw new IllegalArgumentException();
        var commands =
                ((org.w3c.dom.Element) bodies.item(0))
                        .getElementsByTagNameNS("urn:inpay:billing:v1", "CreateInvoice");
        if (commands.getLength() != 1) throw new IllegalArgumentException();
        Map<String, String> fields = new HashMap<>();
        var nodes = commands.item(0).getChildNodes();
        Set<String> allowed =
                Set.of(
                        "operationId",
                        "merchantId",
                        "customerRef",
                        "amount",
                        "method",
                        "dueDate",
                        "contractReference");
        for (int i = 0; i < nodes.getLength(); i++) {
            var node = nodes.item(i);
            if (node instanceof org.w3c.dom.Element e) {
                if (!allowed.contains(e.getLocalName())
                        || fields.putIfAbsent(e.getLocalName(), e.getTextContent()) != null)
                    throw new IllegalArgumentException();
            }
        }
        if (!fields.keySet().equals(allowed) || fields.values().stream().anyMatch(String::isBlank))
            throw new IllegalArgumentException();
        return fields;
    }

    private String envelope(String body) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?><soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\"><soap:Body>"
                + body
                + "</soap:Body></soap:Envelope>";
    }
}
