package br.com.includelabs.inpay.contracts;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Defesa adicional de egress: não substitui contratos tipados nem validação de escopo PCI. */
public final class IntegrationDataPolicy {
    private static final Set<String> FORBIDDEN =
            Set.of(
                    "pan", "cardnumber", "primaryaccountnumber", "cvv", "cvv2", "cvc", "cvc2",
                    "cid", "cav2", "securitycode", "track1", "track2", "trackdata", "fulltrack",
                    "pin", "pinblock", "password", "clientsecret", "accesstoken", "refreshtoken",
                    "authorization", "privatekey", "rawrequest", "rawresponse");
    private static final Pattern CARD_CANDIDATE = Pattern.compile("[0-9][0-9 -]{11,36}[0-9]");

    private IntegrationDataPolicy() {}

    /** Rejeita campos proibidos e sequências compatíveis com PAN, sem reproduzir os dados no erro. */
    public static void validate(Object value) {
        inspect(value, 0, new int[] {0});
    }

    private static void inspect(Object value, int depth, int[] nodes) {
        if (depth > 12 || ++nodes[0] > 2000) {
            throw new IllegalArgumentException("Integration payload exceeds policy");
        }
        if (value instanceof Map<?, ?> map) {
            for (var entry : map.entrySet()) {
                if (!(entry.getKey() instanceof String key)
                        || FORBIDDEN.contains(
                                key.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", ""))) {
                    throw new IllegalArgumentException("Restricted integration field");
                }
                inspect(entry.getValue(), depth + 1, nodes);
            }
        } else if (value instanceof Iterable<?> list) {
            for (Object element : list) {
                inspect(element, depth + 1, nodes);
            }
        } else if (value instanceof CharSequence text) {
            if (text.length() > 8192) {
                throw new IllegalArgumentException("Integration text exceeds policy");
            }
            if (CARD_CANDIDATE.matcher(text).matches() && luhn(text.toString())) {
                throw new IllegalArgumentException("Restricted integration value");
            }
        } else if (value != null
                && !(value instanceof Number)
                && !(value instanceof Boolean)
                && !(value instanceof java.util.UUID)
                && !(value instanceof java.time.temporal.TemporalAccessor)
                && !(value instanceof Enum<?>)) {
            throw new IllegalArgumentException("Unsupported integration value");
        }
    }

    private static boolean luhn(String candidate) {
        String digits = candidate.replaceAll("[^0-9]", "");
        if (digits.length() < 13 || digits.length() > 19) {
            return false;
        }
        int sum = 0;
        boolean doubleDigit = false;
        for (int i = digits.length() - 1; i >= 0; i--) {
            int digit = digits.charAt(i) - '0';
            if (doubleDigit) {
                digit *= 2;
                if (digit > 9) {
                    digit -= 9;
                }
            }
            sum += digit;
            doubleDigit = !doubleDigit;
        }
        return sum % 10 == 0;
    }
}
