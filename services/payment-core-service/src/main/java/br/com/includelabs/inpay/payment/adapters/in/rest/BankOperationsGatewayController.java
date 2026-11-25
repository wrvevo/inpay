package br.com.includelabs.inpay.payment.adapters.in.rest;

import br.com.includelabs.inpay.payment.adapters.out.bank.BankGatewayClient;
import br.com.includelabs.inpay.payment.adapters.out.bank.C6KafkaDispatch;
import br.com.includelabs.inpay.security.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Entrada do gateway. X-Mock true pede ao conector o mock server; ausência ou false segue para o
 * adquirente configurado.
 */
@RestController
@RequestMapping("/gateway/v1/bank-operations")
@Tag(name = "Operações bancárias")
public class BankOperationsGatewayController {
    private static final Logger LOG = LogManager.getLogger(BankOperationsGatewayController.class);
    private final BankGatewayClient client;
    private final TenantContext tenants;
    private final ObjectProvider<C6KafkaDispatch> kafka;

    public BankOperationsGatewayController(
            BankGatewayClient client, TenantContext tenants, ObjectProvider<C6KafkaDispatch> kafka) {
        this.client = client;
        this.tenants = tenants;
        this.kafka = kafka;
    }

    @PostMapping("/{bank}/{operation}")
    @PreAuthorize("hasAuthority('SCOPE_payments/create')")
    @Operation(
            summary = "Executar operação bancária com mock opcional",
            description =
                    "X-Mock true pede o stub de simulação já existente no conector. "
                            + "Ausência ou false segue para o provedor configurado, que permanece desligado até habilitação explícita.")
    public ResponseEntity<String> execute(
            @RequestHeader(value = "X-Merchant-Id", required = false) String tenant,
            @Parameter(
                            name = "X-Mock",
                            in = ParameterIn.HEADER,
                            description = "true usa o stub de simulação do conector; false ou vazio chama o provedor.",
                            example = "true")
                    @RequestHeader(value = "X-Mock", required = false)
                    String mock,
            @RequestHeader(value = "X-Dispatch", required = false) String dispatch,
            @RequestHeader("Idempotency-Key") String key,
            @PathVariable String bank,
            @PathVariable String operation,
            @RequestBody(required = false) Map<String, Object> payload) {
        tenants.resolve(tenant);
        if ("c6".equalsIgnoreCase(bank) && "kafka".equalsIgnoreCase(dispatch)) {
            C6KafkaDispatch publisher = kafka.getIfAvailable();
            if (publisher == null) {
                LOG.error("C6 Kafka dispatch is disabled");
                return ResponseEntity.status(503).body("{\"error\":\"C6_KAFKA_DISABLED\"}");
            }
            publisher.publish(operation, key, mock, payload);
            return ResponseEntity.accepted().body("{\"status\":\"QUEUED\",\"operation\":\"" + operation + "\"}");
        }
        LOG.info("Gateway bank API started bank={} operation={}", bank, operation);
        try {
            BankGatewayClient.Downstream downstream = client.execute(bank, operation, mock, key, payload);
            LOG.info(
                    "Gateway bank API finished bank={} operation={} status={}",
                    bank,
                    operation,
                    downstream.status());
            return ResponseEntity.status(downstream.status()).body(downstream.body());
        } catch (RuntimeException exception) {
            LOG.error("Gateway bank API failed bank={} operation={}", bank, operation, exception);
            throw exception;
        }
    }
}
