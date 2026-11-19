package br.com.includelabs.inpay.connector;

import java.util.Map;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Resposta sanitizada para indisponibilidade de integração externa. */
@RestControllerAdvice
@Order(-1)
public class ProviderErrors {
    /** O consumidor distingue indisponibilidade de um resultado financeiro recusado. */
    @ExceptionHandler(ProviderUnavailableException.class)
    public ResponseEntity<?> unavailable() {
        return ResponseEntity.status(503).body(Map.of("code", "PROVIDER_UNAVAILABLE"));
    }
}
