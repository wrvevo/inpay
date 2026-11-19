package br.com.includelabs.inpay.connector;

import java.util.*;

/** Acesso e cópia de JSON nativo, preservando campos opcionais e separando dados sensíveis. */
public final class JsonFields {

    private JsonFields() {}

    /** Lê um caminho separado por pontos; índices de arrays também são aceitos. */
    public static Object get(Object object, String path) {
        Object current = object;
        for (String key : path.split("\\.")) {
            if (current instanceof Map<?, ?> map) {
                current = map.get(key);
            } else if (current instanceof List<?> list && key.matches("[0-9]+")) {
                int index = Integer.parseInt(key);
                current = index < list.size() ? list.get(index) : null;
            } else {
                return null;
            }
        }
        return current;
    }

    /** Retorna texto vazio para campos ausentes, sem inventar status financeiro. */
    public static String text(Object object, String path) {
        Object value = get(object, path);
        return value == null ? "" : value.toString();
    }

    /** Valida presença de campos nativos obrigatórios antes da chamada externa. */
    public static void require(Map<String, Object> body, String... fields) {
        for (String field : fields) {
            if (get(body, field) == null || text(body, field).isBlank()) {
                throw new IllegalArgumentException("Missing provider field: " + field);
            }
        }
    }

    /** Copia recursivamente o payload sem modificar o objeto recebido pelo controller. */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> copy(Map<String, Object> body) {
        return (Map<String, Object>) transform(body, false);
    }

    /** Remove PAN, CVV e criptogramas antes de gerar a impressão de idempotência. */
    public static Object fingerprintData(Object body) {
        return transform(body, true);
    }

    /** Identifica os campos de cartão que não podem entrar em logs ou persistência. */
    public static boolean sensitive(String key, String parent) {
        String field = key.toLowerCase(Locale.ROOT).replace("_", "");
        return Set.of(
                                "pan",
                                "cardnumber",
                                "cvv",
                                "cvc",
                                "securitycode",
                                "cryptogram",
                                "cavv",
                                "ucaf",
                                "walletkey",
                                "paymentdata",
                                "tokencryptogram",
                                "track1",
                                "track2",
                                "pin",
                                "pinblock")
                        .contains(field)
                || (field.equals("number")
                        && (parent.toLowerCase(Locale.ROOT).contains("card")
                                || parent.equals("acct")));
    }

    /** Bloqueia cartões abertos por padrão e aliases simulados em qualquer campo nativo. */
    public static void validatePayload(Object value, boolean sensitiveAllowed) {
        validate(value, "", sensitiveAllowed);
    }

    private static void validate(Object value, String parent, boolean allowed) {
        if (value instanceof Map<?, ?> map) {
            for (var entry : map.entrySet()) {
                if (!allowed && sensitive(entry.getKey().toString(), parent)) {
                    throw new IllegalArgumentException(
                            "Raw card data requires an explicitly configured PCI environment");
                }
                validate(entry.getValue(), entry.getKey().toString(), allowed);
            }
        } else if (value instanceof List<?> list) {
            for (Object item : list) {
                validate(item, parent, allowed);
            }
        } else if (value instanceof String text && text.startsWith("ptok_")) {
            throw new IllegalArgumentException("InPay simulation aliases are not provider tokens");
        }
    }

    private static Object transform(Object value, boolean redact) {
        return transform(value, redact, "");
    }

    private static Object transform(Object value, boolean redact, String parent) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new TreeMap<>();
            map.forEach(
                    (key, item) ->
                            result.put(
                                    key.toString(),
                                    redact && sensitive(key.toString(), parent)
                                            ? "[OMITTED]"
                                            : transform(item, redact, key.toString())));
            return result;
        }
        if (value instanceof List<?> list) {
            return list.stream().map(item -> transform(item, redact, parent)).toList();
        }
        return value;
    }
}
