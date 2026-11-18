package br.com.includelabs.inpay.token.adapters.in.rest.dto;

import br.com.includelabs.inpay.security.*;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.web.bind.annotation.*;

/**
 * Dados de entrada da operação HTTP. O lojista é resolvido pela autenticação e não pelo corpo da
 * requisição.
 */
@Schema(
        name = "CreateSimulationInstrumentRequest",
        description = "Dados de Request da API TokenController")
public record CreateSimulationInstrumentRequest(
        @Schema(description = "Adquirente de destino, por exemplo CIELO.", example = "CIELO")
                String provider,
        @Schema(description = "Afiliação do lojista na adquirente.", example = "demo")
                String affiliationId) {}
