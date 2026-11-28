package br.com.includelabs.inpay.c6.adapters.in.rest;

import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackageClasses = C6OperationsController.class)
public class C6Errors {
    private static final Logger LOG = LogManager.getLogger(C6Errors.class);

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Map<String, String>> invalid(IllegalArgumentException exception) {
        LOG.error("C6 request rejected");
        return ResponseEntity.unprocessableEntity().body(Map.of("error", "Bank request rejected"));
    }

    @ExceptionHandler(IllegalStateException.class)
    ResponseEntity<Map<String, String>> unavailable(IllegalStateException exception) {
        LOG.error("C6 connector unavailable");
        HttpStatus status =
                exception.getMessage() != null && exception.getMessage().contains("disabled")
                        ? HttpStatus.SERVICE_UNAVAILABLE
                        : HttpStatus.CONFLICT;
        return ResponseEntity.status(status).body(Map.of("error", "Bank request rejected"));
    }
}
