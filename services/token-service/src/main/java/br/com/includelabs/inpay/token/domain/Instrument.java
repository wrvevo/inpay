package br.com.includelabs.inpay.token.domain;

import java.util.UUID;

/**
 * Modelo ou regra de negócio do módulo InPay, independente de Spring e da infraestrutura de
 * persistência.
 */
public record Instrument(
        String token, UUID merchantId, String provider, String affiliationId, boolean active) {

    /**
     * Confirma que o instrumento está ativo e pertence ao mesmo lojista, provedor e afiliação.
     *
     * @param tenant identificador do lojista autorizado para a operação
     * @param destination identificador da adquirente de destino
     * @param affiliation afiliação do lojista na adquirente selecionada
     * @return true quando todas as condições de uso forem atendidas
     */
    public boolean usableBy(UUID tenant, String destination, String affiliation) {
        return active
                && merchantId.equals(tenant)
                && provider.equals(destination)
                && affiliationId.equals(affiliation);
    }
}
