package br.com.includelabs.inpay.contracts;

import java.util.Map;
import java.util.Set;

/** Synthetic routes, not a promise of acquiring or banking contract availability. */
public final class SandboxBindings {
    public static final Set<String> CARDS =
            Set.of("cielo", "rede", "getnet", "stone", "bin", "global-payments", "pagbank", "safrapay");
    public static final Set<String> BANKS =
            Set.of("c6", "itau", "santander", "bb", "bradesco", "caixa", "banrisul");

    private SandboxBindings() {}

    public static void validate(Map<String, String> bindings) {
        if (bindings == null || bindings.isEmpty() || bindings.size() > 5) {
            throw new IllegalArgumentException("Payment bindings required");
        }
        bindings.forEach(
                (method, provider) -> {
                    boolean valid = switch (method) {
                        case "credit_card", "debit_card" -> CARDS.contains(provider);
                        case "pix", "boleto" -> BANKS.contains(provider);
                        case "transfer" -> BANKS.contains(provider) && !"c6".equals(provider);
                        default -> false;
                    };
                    if (!valid) throw new IllegalArgumentException("Unsupported sandbox binding");
                });
    }

    public static String bankOperation(String method, String provider) {
        return switch (method) {
            case "pix" -> "PIX_COB_POST";
            case "boleto" -> switch (provider) {
                case "c6", "itau" -> "BOLETO_CREATE";
                case "santander" -> "BANK_SLIP_CREATE";
                default -> "BOLETO_REGISTER";
            };
            case "transfer" -> switch (provider) {
                case "itau" -> "SISPAG_TED_POST";
                case "santander" -> "PAYMENT_CREATE";
                default -> "TED_POST";
            };
            default -> throw new IllegalArgumentException("Unknown bank payment method");
        };
    }
}
