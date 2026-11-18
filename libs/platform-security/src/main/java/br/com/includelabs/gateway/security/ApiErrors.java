package br.com.includelabs.gateway.security;

import java.util.Map;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.security.access.AccessDeniedException;
@RestControllerAdvice
public class ApiErrors {
 @ExceptionHandler(IllegalArgumentException.class)
 ResponseEntity<?> badInput(IllegalArgumentException e) { return ResponseEntity.unprocessableEntity().body(Map.of("code","INVALID_REQUEST")); }
 @ExceptionHandler(IllegalStateException.class)
 ResponseEntity<?> conflict(IllegalStateException e) { return ResponseEntity.status(409).body(Map.of("code","STATE_CONFLICT")); }
 @ExceptionHandler(NoSuchResource.class)
 ResponseEntity<?> missing() { return ResponseEntity.status(404).body(Map.of("code","NOT_FOUND")); }
 @ExceptionHandler(UnsupportedOperationException.class)
 ResponseEntity<?> unsupported() { return ResponseEntity.status(501).body(Map.of("code","ADAPTER_NOT_IMPLEMENTED")); }
 @ExceptionHandler(AccessDeniedException.class)
 ResponseEntity<?> denied() { return ResponseEntity.status(403).body(Map.of("code","FORBIDDEN")); }
 public static class NoSuchResource extends RuntimeException { }
}
