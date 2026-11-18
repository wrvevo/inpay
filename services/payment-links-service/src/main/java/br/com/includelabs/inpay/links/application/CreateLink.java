package br.com.includelabs.inpay.links.application;

import br.com.includelabs.inpay.links.domain.PaymentLink;
import br.com.includelabs.inpay.links.domain.port.LinkStore;
import java.security.SecureRandom;
import java.time.*;
import java.util.*;

/**
 * Caso de uso responsável por coordenar a regra de negócio e a porta de saída, sem depender dos
 * frameworks de infraestrutura.
 */
public final class CreateLink {

    private final LinkStore linkStore;

    private final SecureRandom random = new SecureRandom();

    /**
     * Inicializa CreateLink com suas dependências explícitas.
     *
     * @param linkStore porta de persistência pertencente ao módulo
     */
    public CreateLink(LinkStore linkStore) {
        this.linkStore = linkStore;
    }

    /**
     * Valida a expiração, gera código aleatório e persiste o link para o lojista autorizado.
     *
     * @param merchantId identificador do lojista autorizado para a operação
     * @param merchantOrderId referência do pedido no sistema do lojista
     * @param amount valor monetário em centavos; nunca utilizar ponto flutuante
     * @param expiresAt instante limite de validade em UTC
     * @return resultado definido pelo contrato; exceções interrompem a operação sem anunciar
     *     sucesso
     * @throws IllegalArgumentException quando a validação ou a capacidade exigida impedir a
     *     conclusão da operação
     */
    public PaymentLink execute(
            UUID merchantId, String merchantOrderId, long amount, Instant expiresAt) {
        if (expiresAt == null || !expiresAt.isAfter(Instant.now())) {
            throw new IllegalArgumentException("Future expiry required");
        }
        byte[] bytes = new byte[24];
        random.nextBytes(bytes);
        var link =
                new PaymentLink(
                        UUID.randomUUID(),
                        merchantId,
                        Base64.getUrlEncoder().withoutPadding().encodeToString(bytes),
                        merchantOrderId,
                        amount,
                        expiresAt,
                        false);
        linkStore.save(link);
        return link;
    }
}
