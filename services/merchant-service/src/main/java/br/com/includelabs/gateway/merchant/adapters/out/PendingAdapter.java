package br.com.includelabs.gateway.merchant.adapters.out;

import java.util.UUID;
import org.springframework.stereotype.Component;
import br.com.includelabs.gateway.merchant.domain.Merchant;
import br.com.includelabs.gateway.merchant.domain.port.MerchantDirectory;
@Component public class PendingAdapter implements MerchantDirectory {
public Merchant find(UUID merchantId){throw new UnsupportedOperationException("Implement MerchantDirectory before enabling this flow");}
}
