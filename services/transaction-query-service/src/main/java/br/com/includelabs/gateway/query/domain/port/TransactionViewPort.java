package br.com.includelabs.gateway.query.domain.port;

import java.util.UUID;
import br.com.includelabs.gateway.query.domain.TransactionView;
public interface TransactionViewPort { TransactionView find(UUID paymentId); }
