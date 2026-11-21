package br.com.includelabs.inpay.credits.adapters.in.rest;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Erros de domínio sem payloads financeiros, segredos ou stack traces na resposta. */
@RestControllerAdvice
public class CreditsErrors {
    /** Rejeita inconsistências antes de anunciar sucesso. */
    @ExceptionHandler({IllegalArgumentException.class, ArithmeticException.class})
    ResponseEntity<?> invalid(RuntimeException exception) {
        return ResponseEntity.unprocessableEntity()
                .body(Map.of("code", "MARKETPLACE_RULE_VIOLATION"));
    }

    /** Dependência não homologada nunca é apresentada como pagamento bem-sucedido. */
    @ExceptionHandler(IllegalStateException.class)
    ResponseEntity<?> unavailable(IllegalStateException exception) {
        return ResponseEntity.status(503).body(Map.of("code", "MARKETPLACE_PROVIDER_UNAVAILABLE"));
    }
}
