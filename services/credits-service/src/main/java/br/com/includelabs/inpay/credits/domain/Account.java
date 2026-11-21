package br.com.includelabs.inpay.credits.domain;

/**
 * Conta operacional de limite e direitos conciliados, sem custódia pelo InPay. Valores em centavos.
 */
public class Account {
    public String owner;
    public String creditor;
    public String contract;
    public boolean blocked;
    public long creditLimit, creditUsed, creditReserved;
    public long promotional, promotionalReserved;
    public long balance, balanceReserved, voucher, voucherReserved;

    /** Calcula disponibilidade por fonte sem misturar crédito, repasses e vouchers. */
    public long available(Source source) {
        return switch (source) {
            case PROMOTIONAL -> promotional - promotionalReserved;
            case CREDIT -> creditLimit - creditUsed - creditReserved;
            case BALANCE -> balance - balanceReserved;
            case VOUCHER -> voucher - voucherReserved;
            case EXTERNAL -> 0;
        };
    }

    /** Reserva fonte interna; usada somente sob bloqueio transacional da conta. */
    public void reserve(Source source, long amount) {
        if (blocked) {
            throw new IllegalArgumentException("Account blocked");
        }
        if (amount <= 0 || (source != Source.EXTERNAL && available(source) < amount)) {
            throw new IllegalArgumentException("Insufficient available amount");
        }
        switch (source) {
            case PROMOTIONAL -> promotionalReserved = Math.addExact(promotionalReserved, amount);
            case CREDIT -> creditReserved = Math.addExact(creditReserved, amount);
            case BALANCE -> balanceReserved = Math.addExact(balanceReserved, amount);
            case VOUCHER -> voucherReserved = Math.addExact(voucherReserved, amount);
            case EXTERNAL -> {}
        }
    }

    /** Libera somente reserva conhecida; não altera dívida ou dinheiro já consumido. */
    public void release(Source source, long amount) {
        switch (source) {
            case PROMOTIONAL -> {
                if (promotionalReserved < amount)
                    throw new IllegalStateException("Invalid reserve");
                promotionalReserved -= amount;
            }
            case CREDIT -> {
                if (creditReserved < amount) {
                    throw new IllegalStateException("Invalid reserve");
                }
                creditReserved -= amount;
            }
            case BALANCE -> {
                if (balanceReserved < amount) {
                    throw new IllegalStateException("Invalid reserve");
                }
                balanceReserved -= amount;
            }
            case VOUCHER -> {
                if (voucherReserved < amount) {
                    throw new IllegalStateException("Invalid reserve");
                }
                voucherReserved -= amount;
            }
            case EXTERNAL -> {}
        }
    }

    /** Efetiva consumo somente após emissão e confirmação das fontes externas. */
    public void consume(Source source, long amount) {
        release(source, amount);
        switch (source) {
            case PROMOTIONAL -> promotional -= amount;
            case CREDIT -> creditUsed = Math.addExact(creditUsed, amount);
            case BALANCE -> balance -= amount;
            case VOUCHER -> voucher -= amount;
            case EXTERNAL -> {}
        }
    }
}
