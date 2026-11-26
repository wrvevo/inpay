package br.com.includelabs.inpay.connector;

import br.com.includelabs.inpay.contracts.MockIntentClient;
import br.com.includelabs.inpay.security.TenantContext;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

/**
 * Local-only, explicitly synthetic tests; never touches live credentials or the financial journal.
 */
@RestController
@Profile("local")
@RequestMapping("/internal/v1/provider-mocks")
public class ProviderMockController {
    private final ProviderSettings settings;
    private final TenantContext tenants;
    private final ObjectMapper mapper = new ObjectMapper();

    /** Inicializa as configurações do provedor e o contexto do lojista. */
    public ProviderMockController(ProviderSettings settings, TenantContext tenants) {
        this.settings = settings;
        this.tenants = tenants;
    }

    /** Executa uma operação sintética sem acessar credenciais reais. */
    @PostMapping("/{operation}")
    @SuppressWarnings("unchecked")
    public Map<String, Object> execute(
            @PathVariable String operation,
            @RequestHeader(value = "X-Mock", required = false) String mock,
            @RequestHeader(value = "X-Merchant-Id", required = false) String merchant) {
        var actor = SecurityContextHolder.getContext().getAuthentication();
        String scope = "SCOPE_connectors-" + settings.name() + "/execute";
        if (actor == null
                || actor.getAuthorities().stream().noneMatch(a -> scope.equals(a.getAuthority()))) {
            throw new AccessDeniedException("Connector execute scope required");
        }
        tenants.resolve(merchant);
        if (!"true".equalsIgnoreCase(mock) || !MockIntentClient.configured()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Mock not enabled");
        }
        if (operation == null || !operation.matches("[A-Z0-9_]+")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown mock intent");
        }
        try {
            var response = MockIntentClient.execute(settings.name(), operation);
            if (response.status() == 404) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown mock intent");
            }
            if (response.status() != 200) {
                throw new IllegalStateException("Mock unavailable");
            }
            Map<String, Object> body = mapper.readValue(response.body(), Map.class);
            if (!Boolean.TRUE.equals(body.get("mocked"))
                    || !Boolean.FALSE.equals(body.get("productionApproved"))
                    || !operation.equals(body.get("operation"))
                    || !settings.name().equals(body.get("provider"))) {
                throw new IllegalStateException("Invalid synthetic response");
            }
            return body;
        } catch (ResponseStatusException failure) {
            throw failure;
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
        } catch (Exception failure) {
            // Do not expose provider configuration or fall back to a real transaction.
        }
        throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Mock unavailable");
    }
}
