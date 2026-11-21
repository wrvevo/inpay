package br.com.includelabs.inpay.accounts.domain;

import java.util.*;

/** Porta exclusiva de Banking. Uma adquirente Pix não implica suporte a contas ou DICT. */
public interface BankingPort {
    enum Operation {
        OPEN_ACCOUNT,
        GET_ACCOUNT,
        BALANCE,
        STATEMENT,
        CREATE_TRANSFER,
        GET_TRANSFER,
        LIST_KEYS,
        CREATE_KEY,
        DELETE_KEY
    }

    /** Executa com tenant autorizado, chave idempotente e identificadores opacos do parceiro. */
    Map<String, Object> execute(
            UUID tenant, Operation operation, String resource, String key, Object request);
}
