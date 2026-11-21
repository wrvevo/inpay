package br.com.includelabs.inpay.payment.application;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;

/**
 * Contratos públicos InPay inspirados na organização da API Pagar.me; sem compatibilidade binária.
 */
public final class GatewayRequests {
    private GatewayRequests() {}

    @Schema(
            description =
                    "Cliente comercial. Não representa conta bancária nem aprovação cadastral.")
    public record Customer(
            @NotBlank @Size(max = 200) String name,
            @Email @NotBlank String email,
            @Pattern(regexp = "individual|company") @NotNull String type,
            @Pattern(regexp = "[0-9]{11}|[0-9]{14}") @NotNull String document) {}

    public record Item(
            @NotBlank @Size(max = 52) String code,
            @NotBlank @Size(max = 200) String description,
            @Positive @Max(100000000) long amount,
            @Min(1) @Max(1000) int quantity) {}

    public record Pix(@Min(60) @Max(86400) int expires_in) {}

    public record Card(
            @NotBlank @Pattern(regexp = "ptok_[A-Za-z0-9_-]{20,100}") String card_token,
            @Min(1) @Max(12) int installments,
            boolean capture) {}

    @Schema(
            description =
                    "Parcela do pedido em centavos. Pix e cartão são mutuamente exclusivos; PAN/CVV não são aceitos.")
    public record Payment(
            @NotNull @Pattern(regexp = "pix|credit_card") String payment_method,
            @Positive @Max(100000000) long amount,
            @Valid Pix pix,
            @Valid Card credit_card) {}

    @Schema(
            description =
                    "Pedido fechado. Até duas cobranças; soma deve coincidir com itens. Registro ainda não executa adquirente.")
    public record Order(
            @NotBlank @Size(max = 52) String code,
            @NotBlank String customer_id,
            @NotEmpty @Size(max = 100) List<@Valid Item> items,
            @NotEmpty @Size(max = 2) List<@Valid Payment> payments) {}

    public record Amount(@Positive long amount) {}
}
