package br.com.includelabs.inpay.resources;

import java.util.*;
import java.util.function.Function;
import tools.jackson.databind.JsonNode;

/** Porta para documentos operacionais isolados por tenant, tipo e serviço. */
public interface ResourceRepository {
    /** Grava documento e outbox uma única vez; o callback roda sob bloqueio da chave. */
    JsonNode create(
            UUID tenant, String kind, String key, Object request, Function<String, Object> factory);

    /** Consulta somente no tenant autenticado. */
    JsonNode get(UUID tenant, String kind, String id);

    /** Paginação limitada; não retorna recursos de outro tenant. */
    List<JsonNode> list(UUID tenant, String kind, int page, int size);
}
