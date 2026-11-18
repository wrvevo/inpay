package br.com.includelabs.inpay.token.application;

import br.com.includelabs.inpay.token.domain.Instrument;
import br.com.includelabs.inpay.token.domain.port.InstrumentStore;
import java.security.SecureRandom;
import java.util.*;

/** Only wired by the local profile. Never represents a real payment instrument. */
public final class CreateSimulationToken {

    private final InstrumentStore instrumentStore;

    /**
     * Inicializa CreateSimulationToken com suas dependências explícitas.
     *
     * @param instrumentStore porta de persistência pertencente ao módulo
     */
    public CreateSimulationToken(InstrumentStore instrumentStore) {
        this.instrumentStore = instrumentStore;
    }

    /**
     * Gera um alias aleatório de 256 bits e persiste o instrumento simulado; não tokeniza cartão
     * real.
     *
     * @param merchantId identificador do lojista autorizado para a operação
     * @param provider identificador da adquirente de destino
     * @param affiliationId afiliação do lojista na adquirente selecionada
     * @return resultado definido pelo contrato; exceções interrompem a operação sem anunciar
     *     sucesso
     * @throws IllegalArgumentException quando a validação ou a capacidade exigida impedir a
     *     conclusão da operação
     */
    public Instrument execute(UUID merchantId, String provider, String affiliationId) {
        if (provider == null || affiliationId == null || affiliationId.isBlank()) {
            throw new IllegalArgumentException();
        }
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        var token =
                new Instrument(
                        "ptok_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes),
                        merchantId,
                        provider,
                        affiliationId,
                        true);
        instrumentStore.save(token);
        return token;
    }
}
