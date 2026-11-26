package br.com.includelabs.inpay.contracts;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Modos de antifraude que o gateway pode selecionar. Não habilita chamada ao provedor real. */
public final class FraudMode {
    public static final String INPAY_RISK = "INPAY_RISK";
    public static final String CLEARSALE = "CLEARSALE";
    public static final String SERASA_EXPERIAN = "SERASA_EXPERIAN";

    private static final Set<String> KNOWN = Set.of(INPAY_RISK, CLEARSALE, SERASA_EXPERIAN);

    private FraudMode() {}

    public static List<String> all() {
        return List.of(INPAY_RISK, CLEARSALE, SERASA_EXPERIAN);
    }

    /** Aceita aliases de configuração e rejeita modo desconhecido. */
    public static String canonical(String raw) {
        String value = raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        value =
                switch (value) {
                    case "", "INPAY", "NATIVE", "INPAY_RISK" -> INPAY_RISK;
                    case "CLEAR_SALE", "CLEARSALE" -> CLEARSALE;
                    case "SERASA", "EXPERIAN", "SERASA_EXPERIENCE", "SERASA_EXPERIAN" ->
                            SERASA_EXPERIAN;
                    default -> value;
                };
        if (!KNOWN.contains(value)) {
            throw new IllegalArgumentException("Unknown fraud mode");
        }
        return value;
    }
}
