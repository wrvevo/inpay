package br.com.includelabs.inpay.bank;

import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class BankErrors {
    private static final Logger LOG = LogManager.getLogger(BankErrors.class);

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Map<String, String>> invalid(IllegalArgumentException exception) {
        LOG.error("Bank request rejected");
        return ResponseEntity.unprocessableEntity().body(Map.of("error", "Bank request rejected"));
    }

    @ExceptionHandler(IllegalStateException.class)
    ResponseEntity<Map<String, String>> conflict(IllegalStateException exception) {
        LOG.error("Bank operation rejected");
        HttpStatus status =
                exception.getMessage() != null && exception.getMessage().contains("disabled")
                        ? HttpStatus.SERVICE_UNAVAILABLE
                        : HttpStatus.CONFLICT;
        return ResponseEntity.status(status).body(Map.of("error", "Bank request rejected"));
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<Map<String, String>> denied(AccessDeniedException exception) {
        LOG.error("Bank request denied");
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Access denied"));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Map<String, String>> unexpected(Exception exception) {
        LOG.error("Unexpected bank connector failure");
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("error", "Bank connector failed"));
    }
}
