package br.com.includelabs.inpay.fraud.adapters.in.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;

/**
 * Dados de entrada da operação HTTP. O lojista é resolvido pela autenticação e não pelo corpo da
 * requisição.
 */
@Schema(name = "AssessRiskRequest", description = "Dados de Request da API FraudController")
public record AssessRiskRequest(
        @Schema(
                        description = "Identificador UUID do pagamento ou recurso.",
                        example = "22222222-2222-2222-2222-222222222222")
                UUID paymentId,
        @Schema(
                        description = "Valor em centavos. Exemplo: 15990 representa R$ 159,90.",
                        example = "15990")
                long amount,
        @Schema(
                        description =
                                "Quantidade de tentativas na última hora, fornecida pelo"
                                        + " chamador interno autorizado.",
                        example = "1")
                int attemptsLastHour,
        @Schema(description = "Quantidade de recusas na última hora.", example = "0")
                int declinesLastHour,
        @Schema(
                        description =
                                "Indica comprometimento conhecido; não deve ser inferido apenas"
                                        + " pela ausência de dados.",
                        example = "false")
                boolean knownCompromised) {}
