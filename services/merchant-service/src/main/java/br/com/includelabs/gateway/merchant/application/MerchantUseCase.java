package br.com.includelabs.gateway.merchant.application;

import java.util.UUID;
import br.com.includelabs.gateway.merchant.domain.Merchant;
import br.com.includelabs.gateway.merchant.domain.port.MerchantDirectory;
public final class MerchantUseCase {
private final MerchantDirectory port;public MerchantUseCase(MerchantDirectory port){this.port=port;}
public Merchant execute(UUID merchantId){return port.find(merchantId);}
}
