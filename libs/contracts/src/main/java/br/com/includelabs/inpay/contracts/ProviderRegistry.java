package br.com.includelabs.inpay.contracts;

import java.util.*;

/** Catálogo único de capacidades implementadas, independente de produto e credenciais. */
public final class ProviderRegistry {
    public enum Capability {
        CARD_PAYMENTS,
        PIX_COLLECTION,
        BOLETO_COLLECTION,
        PIX_REFUND,
        SPLIT,
        BANK_ACCOUNTS,
        PIX_TRANSFER,
        PIX_KEYS
    }

    public record Provider(String id, Set<Capability> implemented) {
        public Provider {
            implemented = Set.copyOf(implemented);
        }
    }

    public record Binding(UUID tenant, String provider, Set<Capability> enabled) {
        public Binding {
            enabled = Set.copyOf(enabled);
        }
    }

    private final Map<String, Provider> providers;

    /** Rejeita duplicidade de adaptadores para evitar roteamento ambíguo. */
    public ProviderRegistry(List<Provider> registered) {
        Map<String, Provider> map = new TreeMap<>();
        for (Provider provider : registered)
            if (map.putIfAbsent(provider.id(), provider) != null)
                throw new IllegalArgumentException("Duplicate provider");
        providers = Map.copyOf(map);
    }

    /** Habilitação exige simultaneamente implementação, configuração e vínculo com o tenant. */
    public Provider require(UUID tenant, Capability capability, Binding binding) {
        Provider provider = providers.get(binding.provider());
        if (!tenant.equals(binding.tenant())
                || provider == null
                || !binding.enabled().contains(capability)
                || !provider.implemented().contains(capability))
            throw new IllegalArgumentException("Provider capability unavailable for tenant");
        return provider;
    }

    /** Inventário técnico; não prova contratação ou habilitação comercial. */
    public List<Provider> catalog() {
        return providers.values().stream().sorted(Comparator.comparing(Provider::id)).toList();
    }

    /**
     * Adaptadores existentes de cartão. Pix/Banking exigem implementação dedicada antes de
     * inclusão.
     */
    public static ProviderRegistry installed() {
        List<Provider> providers = new ArrayList<>();
        for (String id :
                List.of(
                        "cielo",
                        "rede",
                        "getnet",
                        "stone",
                        "bin",
                        "global-payments",
                        "pagbank",
                        "safrapay")) {
            providers.add(
                    new Provider(
                            id,
                            id.equals("pagbank")
                                    ? Set.of(
                                            Capability.CARD_PAYMENTS,
                                            Capability.PIX_COLLECTION,
                                            Capability.BOLETO_COLLECTION)
                                    : id.equals("cielo")
                                            ? Set.of(
                                                    Capability.CARD_PAYMENTS,
                                                    Capability.PIX_COLLECTION)
                                            : Set.of(Capability.CARD_PAYMENTS)));
        }
        Set<Capability> banking =
                Set.of(
                        Capability.PIX_COLLECTION,
                        Capability.BOLETO_COLLECTION,
                        Capability.PIX_REFUND,
                        Capability.BANK_ACCOUNTS,
                        Capability.PIX_TRANSFER,
                        Capability.PIX_KEYS);
        for (String id :
                List.of("c6", "itau", "santander", "bb", "bradesco", "caixa", "banrisul")) {
            providers.add(new Provider(id, banking));
        }
        return new ProviderRegistry(providers);
    }
}
