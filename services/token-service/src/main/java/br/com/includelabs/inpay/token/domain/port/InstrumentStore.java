package br.com.includelabs.inpay.token.domain.port;

import br.com.includelabs.inpay.token.domain.Instrument;
import java.util.*;

/**
 * Porta do domínio para a capacidade de InstrumentStore. Adaptadores de infraestrutura implementam
 * este contrato.
 */
public interface InstrumentStore {

    /**
     * Persiste o registro na base exclusiva deste módulo, preservando o vínculo com o lojista.
     *
     * @param token alias opaco do instrumento ou credencial do contexto; nunca informar PAN ou CVV
     */
    void save(Instrument token);

    /**
     * Consulta o recurso com o filtro de lojista aplicável, impedindo acesso entre tenants.
     *
     * @param tenant identificador do lojista autorizado para a operação
     * @param token alias opaco do instrumento ou credencial do contexto; nunca informar PAN ou CVV
     * @return recurso encontrado no lojista informado, ou Optional vazio quando ausente
     */
    Optional<Instrument> find(UUID tenant, String token);

    /**
     * Revoga o recurso do lojista informado, bloqueando futuras utilizações.
     *
     * @param tenant identificador do lojista autorizado para a operação
     * @param token alias opaco do instrumento ou credencial do contexto; nunca informar PAN ou CVV
     */
    void revoke(UUID tenant, String token);
}
