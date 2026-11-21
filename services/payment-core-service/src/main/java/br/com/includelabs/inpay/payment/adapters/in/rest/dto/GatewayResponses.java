package br.com.includelabs.inpay.payment.adapters.in.rest.dto;

import br.com.includelabs.inpay.payment.application.GatewayRequests.Item;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** Representações documentadas das respostas públicas; não expõem tokens ou dados internos. */
public final class GatewayResponses {
    private GatewayResponses() {}

    @Schema(name = "GatewayCustomerResponse")
    public record Customer(
            @Schema(example = "cus_93b0735c9a064fa18e1fbce4e64f515d") String id,
            String name,
            String email,
            String type,
            String document) {}

    @Schema(name = "GatewayChargeResponse")
    public record Charge(
            String id,
            String order_id,
            long amount,
            String payment_method,
            @Schema(
                            allowableValues = {"pending"},
                            description = "Não indica pagamento aprovado")
                    String status,
            @Schema(allowableValues = {"not_dispatched"}) String processing_status,
            String created_at) {}

    @Schema(name = "GatewayOrderResponse")
    public record Order(
            String id,
            String code,
            String customer_id,
            long amount,
            String currency,
            @Schema(allowableValues = {"pending"}) String status,
            List<Item> items,
            List<Charge> charges,
            String created_at) {}

    @Schema(name = "GatewayPagingResponse")
    public record Paging(int page, int size) {}

    @Schema(name = "GatewayCustomersResponse")
    public record Customers(List<Customer> data, Paging paging) {}

    @Schema(name = "GatewayOrdersResponse")
    public record Orders(List<Order> data, Paging paging) {}

    @Schema(name = "GatewayChargesResponse")
    public record Charges(List<Charge> data, Paging paging) {}
}
