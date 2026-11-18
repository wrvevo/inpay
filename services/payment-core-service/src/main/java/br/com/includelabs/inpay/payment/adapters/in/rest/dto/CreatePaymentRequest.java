package br.com.includelabs.inpay.payment.adapters.in.rest.dto;

import br.com.includelabs.inpay.security.*;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;

/**
 * Dados de entrada da operação HTTP. O lojista é resolvido pela autenticação e não pelo corpo da
 * requisição.
 */
@Schema(name = "CreatePaymentRequest", description = "Dados de Create da API PaymentController")
public record CreatePaymentRequest(
        @NotBlank
                @Size(max = 128)
                @Schema(
                        description = "Referência do pedido no sistema do lojista.",
                        example = "PED-1048")
                String merchantOrderId,
        @Positive
                @Schema(
                        description = "Valor em centavos. Exemplo: 15990 representa R$ 159,90.",
                        example = "15990")
                long amount,
        @Pattern(regexp = "BRL")
                @NotNull
                @Schema(description = "Moeda. Esta versão aceita somente BRL.", example = "BRL")
                String currency,
        @Pattern(regexp = "ptok_[A-Za-z0-9_-]{20,100}")
                @NotNull
                @Schema(
                        description = "Alias opaco ptok_; não enviar número de cartão ou CVV.",
                        example = "ptok_EXEMPLO_SIMULADO_1234567890")
                String instrumentToken) {}
