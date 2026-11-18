package br.com.includelabs.inpay.security;

import java.util.Map;
import org.springframework.http.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;

/**
 * Componente de infraestrutura do InPay responsável por ApiErrors. Não deve ser referenciado pelo
 * domínio.
 */
@RestControllerAdvice
public class ApiErrors {

    /**
     * Converte falha de regra de entrada em HTTP 422 sem revelar o conteúdo sensível da requisição.
     *
     * @param e exceção tratada sem expor detalhes internos ao cliente
     * @return resultado definido pelo contrato; exceções interrompem a operação sem anunciar
     *     sucesso
     */
    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<?> badInput(IllegalArgumentException e) {
        return ResponseEntity.unprocessableEntity().body(Map.of("code", "INVALID_REQUEST"));
    }

    /**
     * Converte conflito de estado ou idempotência em HTTP 409.
     *
     * @param e exceção tratada sem expor detalhes internos ao cliente
     * @return resultado definido pelo contrato; exceções interrompem a operação sem anunciar
     *     sucesso
     */
    @ExceptionHandler(IllegalStateException.class)
    ResponseEntity<?> conflict(IllegalStateException e) {
        return ResponseEntity.status(409).body(Map.of("code", "STATE_CONFLICT"));
    }

    /**
     * Representa recurso não encontrado no tenant autorizado com HTTP 404.
     *
     * @return resultado definido pelo contrato; exceções interrompem a operação sem anunciar
     *     sucesso
     */
    @ExceptionHandler(NoSuchResource.class)
    ResponseEntity<?> missing() {
        return ResponseEntity.status(404).body(Map.of("code", "NOT_FOUND"));
    }

    /**
     * Informa HTTP 501 para fluxo ainda não implementado, sem apresentar sucesso fictício.
     *
     * @return resultado definido pelo contrato; exceções interrompem a operação sem anunciar
     *     sucesso
     */
    @ExceptionHandler(UnsupportedOperationException.class)
    ResponseEntity<?> unsupported() {
        return ResponseEntity.status(501).body(Map.of("code", "ADAPTER_NOT_IMPLEMENTED"));
    }

    /**
     * Informa HTTP 403 quando escopo ou ownership não autorizarem o acesso.
     *
     * @return resultado definido pelo contrato; exceções interrompem a operação sem anunciar
     *     sucesso
     */
    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<?> denied() {
        return ResponseEntity.status(403).body(Map.of("code", "FORBIDDEN"));
    }

    /**
     * Componente de infraestrutura do InPay responsável por NoSuchResource. Não deve ser
     * referenciado pelo domínio.
     */
    public static class NoSuchResource extends RuntimeException {}
}
