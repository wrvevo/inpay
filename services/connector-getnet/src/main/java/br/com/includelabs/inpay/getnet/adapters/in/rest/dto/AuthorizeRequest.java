package br.com.includelabs.inpay.getnet.adapters.in.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;

/**
 * Dados de entrada da operação HTTP. O lojista é resolvido pela autenticação e não pelo corpo da
 * requisição.
 */
@Schema(name = "AuthorizeRequest", description = "Dados de Request da API AuthorizationController")
public record AuthorizeRequest(
        @Schema(
                        description =
                                "UUID estável da operação; repetir o mesmo comando não cria"
                                        + " outra autorização local.",
                        example = "22222222-2222-2222-2222-222222222222")
                UUID operationId,
        @Schema(
                        description = "Valor em centavos. Exemplo: 15990 representa R$ 159,90.",
                        example = "15990")
                long amount,
        @Schema(
                        description = "Alias opaco ptok_; não enviar número de cartão ou CVV.",
                        example = "ptok_EXEMPLO_SIMULADO_1234567890")
                String instrumentToken) {}
