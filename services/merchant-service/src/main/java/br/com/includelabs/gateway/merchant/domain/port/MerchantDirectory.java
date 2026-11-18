package br.com.includelabs.gateway.merchant.domain.port;

import java.util.UUID;
import br.com.includelabs.gateway.merchant.domain.Merchant;
public interface MerchantDirectory { Merchant find(UUID merchantId); }
