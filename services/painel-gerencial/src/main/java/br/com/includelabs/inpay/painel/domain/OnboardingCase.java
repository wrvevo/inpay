package br.com.includelabs.inpay.painel.domain;

import java.util.Map;
import java.util.UUID;

/** Immutable onboarding record. A sandbox status is never a production authorization. */
public record OnboardingCase(
        UUID id,
        UUID storeId,
        String cnpj,
        String legalName,
        String storeName,
        String address,
        Map<String, String> bindings,
        String state,
        String submittedBy,
        String reviewedBy,
        Map<String, String> partners,
        String accountsReceipt,
        UUID demoId,
        Map<String, String> tests,
        long version) {

    /** Copia as coleções para preservar a imutabilidade do processo. */
    public OnboardingCase {
        bindings = Map.copyOf(bindings);
        partners = Map.copyOf(partners);
        tests = Map.copyOf(tests);
    }

    /** Cria a próxima versão do processo com o novo estado e evidências. */
    public OnboardingCase transition(
            String next,
            String submitter,
            String reviewer,
            Map<String, String> partnerEvidence,
            String receipt,
            UUID demo,
            Map<String, String> evidence) {
        return new OnboardingCase(
                id,
                storeId,
                cnpj,
                legalName,
                storeName,
                address,
                bindings,
                next,
                submitter,
                reviewer,
                partnerEvidence,
                receipt,
                demo,
                evidence,
                version + 1);
    }

    /** Cria uma nova versão com os vínculos de pagamento atualizados. */
    public OnboardingCase configure(Map<String, String> next) {
        return new OnboardingCase(
                id,
                storeId,
                cnpj,
                legalName,
                storeName,
                address,
                next,
                state,
                submittedBy,
                reviewedBy,
                partners,
                accountsReceipt,
                demoId,
                tests,
                version + 1);
    }
}
