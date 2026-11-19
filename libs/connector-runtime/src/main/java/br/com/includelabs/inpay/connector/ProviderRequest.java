package br.com.includelabs.inpay.connector;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;
import java.util.UUID;

/** Envelope interno. O payload usa o contrato nativo e preserva seus campos opcionais. */
@Schema(
        description =
                "Comando para API externa; exemplos completos em resources/provider-examples. Nunca registrar o payload em logs.")
public record ProviderRequest(
        @Schema(description = "UUID único e estável por operação financeira") UUID operationId,
        @Schema(
                        description =
                                "Objeto JSON nativo do provedor, sem credenciais. Tokens devem pertencer à adquirente e estabelecimento configurados.")
                Map<String, Object> payload) {
    /** Impede vazamento acidental do corpo através do toString gerado para records. */
    @Override
    public String toString() {
        return "ProviderRequest[operationId=" + operationId + "]";
    }
}
