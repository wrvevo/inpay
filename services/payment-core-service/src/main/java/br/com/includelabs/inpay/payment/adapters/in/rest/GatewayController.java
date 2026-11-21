package br.com.includelabs.inpay.payment.adapters.in.rest;

import br.com.includelabs.inpay.payment.application.GatewayOrders;
import br.com.includelabs.inpay.payment.application.GatewayRequests.*;
import br.com.includelabs.inpay.resources.ResourceRepository;
import br.com.includelabs.inpay.security.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;

/**
 * API pública de Gateway. Clientes, pedidos e cobranças não são contas Accounts ou benefícios
 * Credits.
 */
@RestController
@RequestMapping("/gateway/v1")
@Tag(
        name = "INPAY Gateway",
        description =
                "Clientes, pedidos e cobranças. Valores em centavos; recursos isolados por lojista.")
public class GatewayController {
    private final jakarta.servlet.http.HttpServletRequest http;
    private final ResourceRepository store;
    private final TenantContext tenants;
    private final GatewayOrders orders;

    public GatewayController(
            ResourceRepository store,
            TenantContext tenants,
            GatewayOrders orders,
            jakarta.servlet.http.HttpServletRequest http) {
        this.http = http;
        this.store = store;
        this.tenants = tenants;
        this.orders = orders;
    }

    /** Registra cliente comercial idempotente, sem abrir conta financeira. */
    @PostMapping("/customers")
    @PreAuthorize("hasAuthority('SCOPE_payments/create')")
    @Operation(
            tags = {"Clientes"},
            summary = "Criar cliente",
            description =
                    "Escopo payments/create. Idempotency-Key obrigatório; não realiza KYC bancário.")
    @ApiResponse(
            responseCode = "201",
            description = "Cliente registrado",
            content =
                    @io.swagger.v3.oas.annotations.media.Content(
                            schema =
                                    @io.swagger.v3.oas.annotations.media.Schema(
                                            implementation =
                                                    br.com.includelabs.inpay.payment.adapters.in
                                                            .rest.dto.GatewayResponses.Customer
                                                            .class)))
    public ResponseEntity<JsonNode> customer(
            @RequestHeader("Idempotency-Key") String key, @Valid @RequestBody Customer body) {
        return ResponseEntity.status(201)
                .body(
                        store.create(
                                tenants.resolve(http.getHeader("X-Merchant-Id")),
                                "cus",
                                key,
                                body,
                                id ->
                                        Map.of(
                                                "id",
                                                id,
                                                "name",
                                                body.name(),
                                                "email",
                                                body.email(),
                                                "type",
                                                body.type(),
                                                "document",
                                                body.document())));
    }

    /** Registra pedido e suas cobranças em uma única transação. */
    @PostMapping("/orders")
    @PreAuthorize("hasAuthority('SCOPE_payments/create')")
    @Operation(
            tags = {"Pedidos"},
            summary = "Criar pedido com cartão ou Pix",
            description =
                    "payments/create. Aceita até duas parcelas de pagamento. Retorna pending/not_dispatched; não gera QR nem autoriza cartão até a integração da saga de execução.")
    @ApiResponse(
            responseCode = "202",
            description = "Registrado; pagamento ainda não executado",
            content =
                    @io.swagger.v3.oas.annotations.media.Content(
                            schema =
                                    @io.swagger.v3.oas.annotations.media.Schema(
                                            implementation =
                                                    br.com.includelabs.inpay.payment.adapters.in
                                                            .rest.dto.GatewayResponses.Order
                                                            .class)))
    public ResponseEntity<JsonNode> order(
            @RequestHeader("Idempotency-Key") String key, @Valid @RequestBody Order body) {
        return ResponseEntity.accepted()
                .body(orders.create(tenants.resolve(http.getHeader("X-Merchant-Id")), key, body));
    }

    /** Lista customers no tenant autenticado, com paginação estável. */
    @GetMapping("/customers")
    @PreAuthorize("hasAuthority('SCOPE_payments/read')")
    @Operation(
            tags = {"Clientes"},
            summary = "Listar clientes",
            description = "payments/read. page >= 1; size entre 1 e 100.")
    @ApiResponse(
            responseCode = "200",
            description = "Página de recursos",
            content =
                    @io.swagger.v3.oas.annotations.media.Content(
                            schema =
                                    @io.swagger.v3.oas.annotations.media.Schema(
                                            implementation =
                                                    br.com.includelabs.inpay.payment.adapters.in
                                                            .rest.dto.GatewayResponses.Customers
                                                            .class)))
    public Map<String, Object> listCustomers(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Map.of(
                "data",
                store.list(tenants.resolve(http.getHeader("X-Merchant-Id")), "cus", page, size),
                "paging",
                Map.of("page", page, "size", size));
    }

    /** Consulta customers sem permitir acesso entre tenants. */
    @GetMapping("/customers/{id}")
    @PreAuthorize("hasAuthority('SCOPE_payments/read')")
    @Operation(
            tags = {"Clientes"},
            summary = "Obter clientes",
            description = "payments/read. O identificador é uma string opaca.")
    @ApiResponse(
            responseCode = "200",
            description = "Recurso encontrado",
            content =
                    @io.swagger.v3.oas.annotations.media.Content(
                            schema =
                                    @io.swagger.v3.oas.annotations.media.Schema(
                                            implementation =
                                                    br.com.includelabs.inpay.payment.adapters.in
                                                            .rest.dto.GatewayResponses.Customer
                                                            .class)))
    public JsonNode getCustomer(@PathVariable String id) {
        return store.get(tenants.resolve(http.getHeader("X-Merchant-Id")), "cus", id);
    }

    /** Lista orders no tenant autenticado, com paginação estável. */
    @GetMapping("/orders")
    @PreAuthorize("hasAuthority('SCOPE_payments/read')")
    @Operation(
            tags = {"Pedidos"},
            summary = "Listar pedidos",
            description = "payments/read. page >= 1; size entre 1 e 100.")
    @ApiResponse(
            responseCode = "200",
            description = "Página de recursos",
            content =
                    @io.swagger.v3.oas.annotations.media.Content(
                            schema =
                                    @io.swagger.v3.oas.annotations.media.Schema(
                                            implementation =
                                                    br.com.includelabs.inpay.payment.adapters.in
                                                            .rest.dto.GatewayResponses.Orders
                                                            .class)))
    public Map<String, Object> listOrders(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Map.of(
                "data",
                store.list(tenants.resolve(http.getHeader("X-Merchant-Id")), "or", page, size),
                "paging",
                Map.of("page", page, "size", size));
    }

    /** Consulta orders sem permitir acesso entre tenants. */
    @GetMapping("/orders/{id}")
    @PreAuthorize("hasAuthority('SCOPE_payments/read')")
    @Operation(
            tags = {"Pedidos"},
            summary = "Obter pedidos",
            description = "payments/read. O identificador é uma string opaca.")
    @ApiResponse(
            responseCode = "200",
            description = "Recurso encontrado",
            content =
                    @io.swagger.v3.oas.annotations.media.Content(
                            schema =
                                    @io.swagger.v3.oas.annotations.media.Schema(
                                            implementation =
                                                    br.com.includelabs.inpay.payment.adapters.in
                                                            .rest.dto.GatewayResponses.Order
                                                            .class)))
    public JsonNode getOrder(@PathVariable String id) {
        return store.get(tenants.resolve(http.getHeader("X-Merchant-Id")), "or", id);
    }

    /** Lista charges no tenant autenticado, com paginação estável. */
    @GetMapping("/charges")
    @PreAuthorize("hasAuthority('SCOPE_payments/read')")
    @Operation(
            tags = {"Cobranças"},
            summary = "Listar cobranças",
            description = "payments/read. page >= 1; size entre 1 e 100.")
    @ApiResponse(
            responseCode = "200",
            description = "Página de recursos",
            content =
                    @io.swagger.v3.oas.annotations.media.Content(
                            schema =
                                    @io.swagger.v3.oas.annotations.media.Schema(
                                            implementation =
                                                    br.com.includelabs.inpay.payment.adapters.in
                                                            .rest.dto.GatewayResponses.Charges
                                                            .class)))
    public Map<String, Object> listCharges(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Map.of(
                "data",
                store.list(tenants.resolve(http.getHeader("X-Merchant-Id")), "ch", page, size),
                "paging",
                Map.of("page", page, "size", size));
    }

    /** Consulta charges sem permitir acesso entre tenants. */
    @GetMapping("/charges/{id}")
    @PreAuthorize("hasAuthority('SCOPE_payments/read')")
    @Operation(
            tags = {"Cobranças"},
            summary = "Obter cobranças",
            description = "payments/read. O identificador é uma string opaca.")
    @ApiResponse(
            responseCode = "200",
            description = "Recurso encontrado",
            content =
                    @io.swagger.v3.oas.annotations.media.Content(
                            schema =
                                    @io.swagger.v3.oas.annotations.media.Schema(
                                            implementation =
                                                    br.com.includelabs.inpay.payment.adapters.in
                                                            .rest.dto.GatewayResponses.Charge
                                                            .class)))
    public JsonNode getCharge(@PathVariable String id) {
        return store.get(tenants.resolve(http.getHeader("X-Merchant-Id")), "ch", id);
    }

    /** Nunca retorna captura fictícia enquanto a orquestração não foi conectada à adquirente. */
    @PostMapping("/charges/{id}/capture")
    @PreAuthorize("hasAuthority('SCOPE_payments/capture')")
    @Operation(
            tags = {"Cobranças"},
            summary = "Capturar cobrança",
            description =
                    "Contrato reservado; execução ainda indisponível. Escopo payments/capture.")
    @ApiResponse(responseCode = "501", description = "Orquestração de captura não habilitada")
    public void capture(
            @PathVariable String id,
            @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody Amount amount) {
        store.get(tenants.resolve(http.getHeader("X-Merchant-Id")), "ch", id);
        throw new UnsupportedOperationException();
    }

    /**
     * Cancelamento/estorno deve ser confirmado pelo provedor; este contrato falha explicitamente.
     */
    @DeleteMapping("/charges/{id}")
    @PreAuthorize("hasAuthority('SCOPE_payments/refund')")
    @Operation(
            tags = {"Cobranças"},
            summary = "Cancelar ou estornar cobrança",
            description =
                    "Contrato reservado; execução ainda indisponível. Escopo payments/refund.")
    @ApiResponse(responseCode = "501", description = "Orquestração de estorno não habilitada")
    public void refund(
            @PathVariable String id,
            @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody Amount amount) {
        store.get(tenants.resolve(http.getHeader("X-Merchant-Id")), "ch", id);
        throw new UnsupportedOperationException();
    }
}
