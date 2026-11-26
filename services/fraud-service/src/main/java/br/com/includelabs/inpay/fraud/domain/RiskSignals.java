package br.com.includelabs.inpay.fraud.domain;

import java.util.Set;

/**
 * Sinais agregados enviados pelo checkout. Não transporta PAN, CVV, trajetória bruta do mouse nem
 * sequência de teclas.
 */
public final class RiskSignals {
    private static final Set<String> DISPOSABLE =
            Set.of(
                    "mailinator.com",
                    "guerrillamail.com",
                    "tempmail.com",
                    "yopmail.com",
                    "10minutemail.com");

    private RiskSignals() {}

    public record Behavior(
            int typingIntervalMs, int mouseEntropy, int navigationSeconds, boolean unusualJourney) {
        public Behavior {
            if (typingIntervalMs < 0
                    || typingIntervalMs > 60000
                    || mouseEntropy < 0
                    || mouseEntropy > 100
                    || navigationSeconds < 0
                    || navigationSeconds > 86400) {
                throw new IllegalArgumentException("Invalid behavior signals");
            }
        }
    }

    public record Device(
            String deviceKey,
            String country,
            String homeCountry,
            boolean vpnOrProxy,
            int seenAtOtherMerchants) {
        public Device {
            deviceKey = deviceKey == null ? "" : deviceKey;
            country = country == null ? "" : country.trim().toUpperCase();
            homeCountry =
                    homeCountry == null || homeCountry.isBlank()
                            ? "BR"
                            : homeCountry.trim().toUpperCase();
            if (!deviceKey.matches("[A-Za-z0-9_\\-]{0,128}")
                    || !country.matches("[A-Z]{0,2}")
                    || !homeCountry.matches("[A-Z]{2}")
                    || seenAtOtherMerchants < 0
                    || seenAtOtherMerchants > 10000) {
                throw new IllegalArgumentException("Invalid device signals");
            }
        }
    }

    public record Identity(
            String cpf,
            String emailDomain,
            boolean deliveryMatchesBilling,
            int cardholderMatch,
            int priorPurchases) {
        public Identity {
            cpf = cpf == null ? "" : cpf.replaceAll("\\D", "");
            emailDomain = emailDomain == null ? "" : emailDomain.trim().toLowerCase();
            if ((!cpf.isEmpty() && !cpf.matches("\\d{11}"))
                    || emailDomain.length() > 80
                    || (!emailDomain.isEmpty() && !emailDomain.matches("[a-z0-9.-]+"))
                    || cardholderMatch < 0
                    || cardholderMatch > 100
                    || priorPurchases < 0
                    || priorPurchases > 100000) {
                throw new IllegalArgumentException("Invalid identity signals");
            }
        }

        public String cpfStatus() {
            return documentStatus(cpf);
        }

        public boolean disposableEmail() {
            return DISPOSABLE.contains(emailDomain);
        }
    }

    public static String cpfStatus(String digits) {
        return documentStatus(digits);
    }

    private static String documentStatus(String digits) {
        if (digits == null || digits.isBlank()) {
            return "MISSING";
        }
        if (!digits.matches("\\d{11}") || digits.chars().distinct().count() == 1) {
            return "INVALID";
        }
        if (checkDigit(digits, 9) != digits.charAt(9) - '0'
                || checkDigit(digits, 10) != digits.charAt(10) - '0') {
            return "INVALID";
        }
        return "VALID";
    }

    private static int checkDigit(String digits, int length) {
        int weight = length + 1;
        int sum = 0;
        for (int i = 0; i < length; i++) {
            sum += (digits.charAt(i) - '0') * (weight - i);
        }
        int mod = (sum * 10) % 11;
        return mod == 10 ? 0 : mod;
    }
}
