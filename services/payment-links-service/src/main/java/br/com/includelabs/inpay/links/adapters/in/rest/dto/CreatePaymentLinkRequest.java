package br.com.includelabs.inpay.links.adapters.in.rest.dto;

import br.com.includelabs.inpay.security.*;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.*;
import org.springframework.web.bind.annotation.*;

/**
 * Dados de entrada da operação HTTP. O lojista é resolvido pela autenticação e não pelo corpo da
 * requisição.
 */
@Schema(name = "CreatePaymentLinkRequest", description = "Dados de Request da API LinkController")
public record CreatePaymentLinkRequest(
        @Schema(description = "Referência do pedido no sistema do lojista.", example = "PED-1048")
                String merchantOrderId,
        @Schema(
                        description = "Valor em centavos. Exemplo: 15990 representa R$ 159,90.",
                        example = "15990")
                long amount,
        @Schema(
                        description =
                                "Validade em UTC, no formato ISO 8601; deve estar no futuro ao"
                                        + " criar o link.",
                        example = "2027-01-01T23:59:59Z")
                Instant expiresAt) {}
