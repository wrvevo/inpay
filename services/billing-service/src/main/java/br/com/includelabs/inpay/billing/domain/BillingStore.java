package br.com.includelabs.inpay.billing.domain;

import java.time.LocalDate;
import java.util.*;

/**
 * Porta de consulta operacional para o painel financeiro, sem acesso aos bancos de
 * Accounts/Credits.
 */
public interface BillingStore {
    Map<String, Object> dashboard(int page, int size);

    Map<String, Object> invoice(UUID id);

    UUID merchantTenant(UUID id);

    List<Map<String, Object>> resources(String type, int page, int size);

    Map<String, Object> cashflow(LocalDate from, LocalDate to);
}
