package br.com.includelabs.inpay.stone.adapters.out;

import br.com.includelabs.inpay.connector.*;
import br.com.includelabs.inpay.connector.domain.ProviderOperation;
import br.com.includelabs.inpay.stone.domain.Authorization;
import br.com.includelabs.inpay.stone.domain.port.AcquirerPort;
import java.util.Set;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Adaptador real para a porta de autorização existente. O simulador permanece exclusivo do perfil
 * local.
 */
@Component
@Profile("!local")
public class ExternalAcquirer implements AcquirerPort {

    private final ProviderService service;

    /** Injeta o caso de uso com idempotência durável e autenticação do provedor. */
    public ExternalAcquirer(ProviderService service) {
        this.service = service;
    }

    /**
     * Confere o valor interno, envia o JSON nativo e retorna somente o resultado sem dados de
     * cartão.
     */
    @Override
    public Authorization authorize(Authorization request) {
        if (request.providerPayload() == null) {
            throw new IllegalArgumentException("providerPayload is required outside local profile");
        }
        String value = JsonFields.text(request.providerPayload(), "amount");
        if (Long.parseLong(value) != request.amount()) {
            throw new IllegalArgumentException("Provider amount differs from InPay amount");
        }
        var result =
                service.execute(
                        request.merchantId(),
                        request.operationId(),
                        ProviderOperation.AUTHORIZE,
                        null,
                        request.providerPayload());
        var outcome = Authorization.Outcome.UNKNOWN;
        if (!result.uncertain() && Set.of("authorized", "paid").contains(result.status())) {
            outcome = Authorization.Outcome.AUTHORIZED;
        } else if (Set.of(
                        "DECLINED",
                        "DENIED",
                        "NOT_AUTHORIZED",
                        "NotAuthorized",
                        "3",
                        "NEG",
                        "declined",
                        "refused")
                .contains(result.status())) {
            outcome = Authorization.Outcome.DECLINED;
        }
        return new Authorization(
                request.operationId(),
                request.merchantId(),
                request.amount(),
                request.instrumentToken(),
                outcome,
                null,
                result);
    }
}
