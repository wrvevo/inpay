package br.com.includelabs.gateway.query.adapters.out;

import java.util.UUID;
import org.springframework.stereotype.Component;
import br.com.includelabs.gateway.query.domain.TransactionView;
import br.com.includelabs.gateway.query.domain.port.TransactionViewPort;
@Component public class PendingAdapter implements TransactionViewPort {
public TransactionView find(UUID paymentId){throw new UnsupportedOperationException("Implement TransactionViewPort before enabling this flow");}
}
