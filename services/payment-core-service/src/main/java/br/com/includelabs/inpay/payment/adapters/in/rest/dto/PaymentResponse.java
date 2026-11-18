package br.com.includelabs.inpay.payment.adapters.in.rest.dto;

import br.com.includelabs.inpay.payment.domain.Payment;
import br.com.includelabs.inpay.security.*;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;

/**
 * Resposta pública do pagamento, sem expor o alias de instrumento nem dados internos da adquirente.
 */
@Schema(name = "PaymentResponse", description = "Dados de View da API PaymentController")
public record PaymentResponse(
        @Schema(
                        description = "Identificador UUID do pagamento ou recurso.",
                        example = "22222222-2222-2222-2222-222222222222")
                UUID id,
        @Schema(
                        description =
                                "Estado do pagamento. CREATED nesta versão não significa"
                                        + " cobrança realizada.",
                        example = "CREATED")
                String status,
        @Schema(
                        description = "Valor em centavos. Exemplo: 15990 representa R$ 159,90.",
                        example = "15990")
                long amount,
        @Schema(description = "Moeda. Esta versão aceita somente BRL.", example = "BRL")
                String currency,
        @Schema(
                        description =
                                "Versão do agregado, utilizada no controle de concorrência e"
                                        + " projeções.",
                        example = "0")
                long version) {

    /**
     * Converte o modelo de domínio para a resposta pública sem expor o token do instrumento.
     *
     * @param payment agregado de pagamento com identidade, valor e último estado confirmado
     * @return representação pública do resultado, limitada aos dados previstos no contrato HTTP
     */
    public static PaymentResponse of(Payment payment) {
        return new PaymentResponse(
                payment.id(),
                payment.status().name(),
                payment.amount(),
                payment.currency(),
                payment.version());
    }
}
