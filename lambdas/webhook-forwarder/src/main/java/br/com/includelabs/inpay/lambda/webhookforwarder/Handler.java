package br.com.includelabs.inpay.lambda.webhookforwarder;

import com.amazonaws.services.lambda.runtime.*;
import java.util.Map;

/** Deliberately fails closed until provider verification and durable ingress are configured. */
public class Handler implements RequestHandler<Map<String, Object>, Map<String, Object>> {

    /**
     * Recusa callbacks enquanto a verificação do provedor e a persistência durável não estiverem
     * implementadas.
     *
     * @param event evento recebido, sem dados de cartão nem tokens de autenticação
     * @param context contexto de execução fornecido pela AWS Lambda
     * @return resultado definido pelo contrato; exceções interrompem a operação sem anunciar
     *     sucesso
     */
    public Map<String, Object> handleRequest(Map<String, Object> event, Context context) {
        return Map.of(
                "statusCode", 501, "body", "{\"code\":\"PROVIDER_VERIFICATION_NOT_IMPLEMENTED\"}");
    }
}
