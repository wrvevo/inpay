package br.com.includelabs.inpay.itau.adapters.in.rest;

import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackageClasses = ItauOperationsController.class)
public class ItauErrors {
    private static final Logger LOG = LogManager.getLogger(ItauErrors.class);

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Map<String, String>> invalid(IllegalArgumentException exception) {
        LOG.error("Itau request rejected", exception);
        return ResponseEntity.unprocessableEntity().body(Map.of("error", exception.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    ResponseEntity<Map<String, String>> unavailable(IllegalStateException exception) {
        LOG.error("Itau connector unavailable", exception);
        HttpStatus status =
                exception.getMessage() != null && exception.getMessage().contains("disabled")
                        ? HttpStatus.SERVICE_UNAVAILABLE
                        : HttpStatus.CONFLICT;
        return ResponseEntity.status(status).body(Map.of("error", exception.getMessage()));
    }
}
