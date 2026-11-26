package br.com.includelabs.inpay.fraud.adapters.in.rest;

import br.com.includelabs.inpay.contracts.FraudMode;
import br.com.includelabs.inpay.fraud.application.AssessRisk;
import br.com.includelabs.inpay.fraud.domain.port.FraudModeSource;
import br.com.includelabs.inpay.security.TenantContext;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/** Seleção do modo nativo e registro de chargeback para o aprendizado do InPay Risk. */
@RestController
public class FraudModeController {
    private final FraudModeSource modes;
    private final AssessRisk assessRisk;
    private final TenantContext tenantContext;

    public FraudModeController(
            FraudModeSource modes, AssessRisk assessRisk, TenantContext tenantContext) {
        this.modes = modes;
        this.assessRisk = assessRisk;
        this.tenantContext = tenantContext;
    }

    @GetMapping("/internal/v1/fraud-mode")
    @PreAuthorize("hasAuthority('SCOPE_fraud/read')")
    public Map<String, Object> current() {
        return catalog(modes.current());
    }

    @PostMapping("/internal/v1/fraud-mode")
    @PreAuthorize("hasAuthority('SCOPE_fraud/evaluate')")
    public Map<String, Object> select(@RequestBody Map<String, String> body) {
        return catalog(modes.select(body.get("mode")));
    }

    @PostMapping("/internal/v1/chargebacks")
    @PreAuthorize("hasAuthority('SCOPE_fraud/evaluate')")
    public Map<String, String> chargeback(
            @RequestBody Map<String, String> body,
            @RequestHeader(value = "X-Merchant-Id", required = false) String merchantId) {
        assessRisk.learn(
                tenantContext.resolve(merchantId),
                body.get("deviceKey"),
                "CONFIRMED".equalsIgnoreCase(body.get("outcome")));
        return Map.of("status", "RECORDED");
    }

    private static Map<String, Object> catalog(String mode) {
        return Map.of(
                "mode",
                mode,
                "providers",
                FraudMode.all(),
                "note",
                "InPay Risk mede tokens do modelo local. ClearSale e Serasa Experian usam stub"
                        + " enquanto a chamada real está desligada. O preço fica nos planos.");
    }
}
