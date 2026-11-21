package br.com.includelabs.inpay.accounts.adapters.in.rest;

import br.com.includelabs.inpay.accounts.domain.BankingPort;
import br.com.includelabs.inpay.accounts.domain.BankingPort.Operation;
import br.com.includelabs.inpay.security.TenantContext;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** Contas financeiras do parceiro, isoladas de clientes comerciais e créditos promocionais. */
@RestController
@RequestMapping("/accounts/v1")
@Tag(
        name = "INPAY Accounts",
        description =
                "Operações dependem de parceiro Banking; atualmente indisponíveis, com resposta 503.")
@ApiResponse(
        responseCode = "503",
        description = "Parceiro financeiro não configurado; nenhum efeito financeiro")
public class AccountsController {
    private final jakarta.servlet.http.HttpServletRequest http;
    private final BankingPort bank;
    private final TenantContext tenants;

    public AccountsController(
            BankingPort bank, TenantContext tenants, jakarta.servlet.http.HttpServletRequest http) {
        this.http = http;
        this.bank = bank;
        this.tenants = tenants;
    }

    public record Opening(
            @NotBlank @Pattern(regexp = "individual|company") String type,
            @NotBlank String name,
            @NotBlank @Pattern(regexp = "[0-9]{11}|[0-9]{14}") String document,
            @Email @NotBlank String email,
            @NotBlank String onboarding_reference) {}

    public record Transfer(
            @Positive long amount,
            @NotBlank String destination_key,
            @NotBlank String description) {}

    public record PixKey(
            @NotBlank @Pattern(regexp = "cpf|cnpj|email|phone|evp") String type, String value) {}

    /** Encaminha abertura PF/PJ; onboarding_reference referencia dossiê seguro no parceiro. */
    @PostMapping("/accounts")
    @PreAuthorize("hasAuthority('SCOPE_accounts/write')")
    @io.swagger.v3.oas.annotations.Operation(
            summary = "Solicitar abertura de conta PF/PJ",
            description =
                    "accounts/write. KYC, documentos e decisão pertencem ao parceiro; cadastro não é aprovação.")
    public Map<String, Object> open(
            @RequestHeader("Idempotency-Key") String key, @Valid @RequestBody Opening body) {
        return bank.execute(
                tenants.resolve(http.getHeader("X-Merchant-Id")),
                Operation.OPEN_ACCOUNT,
                null,
                key,
                body);
    }

    /** Consulta conta no contexto do tenant; o adaptador deve validar também a titularidade. */
    @GetMapping("/accounts/{id}")
    @PreAuthorize("hasAuthority('SCOPE_accounts/read')")
    @io.swagger.v3.oas.annotations.Operation(summary = "Obter conta")
    public Map<String, Object> get(@PathVariable String id) {
        return bank.execute(
                tenants.resolve(http.getHeader("X-Merchant-Id")),
                Operation.GET_ACCOUNT,
                id,
                null,
                null);
    }

    /** Consulta saldo confirmado pelo custodiante, sem inferir a partir do crédito promocional. */
    @GetMapping("/accounts/{id}/balance")
    @PreAuthorize("hasAuthority('SCOPE_accounts/read')")
    @io.swagger.v3.oas.annotations.Operation(summary = "Consultar saldo da conta financeira")
    public Map<String, Object> balance(@PathVariable String id) {
        return bank.execute(
                tenants.resolve(http.getHeader("X-Merchant-Id")),
                Operation.BALANCE,
                id,
                null,
                null);
    }

    /** Extrato paginado deve refletir lançamentos do parceiro financeiro. */
    @GetMapping("/accounts/{id}/statement")
    @PreAuthorize("hasAuthority('SCOPE_accounts/read')")
    @io.swagger.v3.oas.annotations.Operation(summary = "Consultar extrato")
    public Map<String, Object> statement(
            @PathVariable String id, @RequestParam(defaultValue = "1") int page) {
        if (page < 1) throw new IllegalArgumentException();
        return bank.execute(
                tenants.resolve(http.getHeader("X-Merchant-Id")),
                Operation.STATEMENT,
                id,
                null,
                Map.of("page", page));
    }

    /** Transferência Pix de saída é distinta da cobrança Pix do Gateway. */
    @PostMapping("/accounts/{id}/transfers")
    @PreAuthorize("hasAuthority('SCOPE_accounts/transfer')")
    @io.swagger.v3.oas.annotations.Operation(
            summary = "Solicitar transferência Pix",
            description =
                    "accounts/transfer. Chave idempotente obrigatória; confirmação depende do parceiro.")
    public Map<String, Object> transfer(
            @PathVariable String id,
            @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody Transfer body) {
        return bank.execute(
                tenants.resolve(http.getHeader("X-Merchant-Id")),
                Operation.CREATE_TRANSFER,
                id,
                key,
                body);
    }

    /** Consulta o resultado de uma transferência sem repetir uma saída de dinheiro. */
    @GetMapping("/accounts/{id}/transfers/{transferId}")
    @PreAuthorize("hasAuthority('SCOPE_accounts/read')")
    @io.swagger.v3.oas.annotations.Operation(summary = "Consultar transferência Pix")
    public Map<String, Object> transfer(@PathVariable String id, @PathVariable String transferId) {
        return bank.execute(
                tenants.resolve(http.getHeader("X-Merchant-Id")),
                Operation.GET_TRANSFER,
                id,
                null,
                Map.of("transfer_id", transferId));
    }

    /** Lista chaves mantidas pelo parceiro. */
    @GetMapping("/accounts/{id}/pix-keys")
    @PreAuthorize("hasAuthority('SCOPE_accounts/read')")
    @io.swagger.v3.oas.annotations.Operation(summary = "Listar chaves Pix")
    public Map<String, Object> keys(@PathVariable String id) {
        return bank.execute(
                tenants.resolve(http.getHeader("X-Merchant-Id")),
                Operation.LIST_KEYS,
                id,
                null,
                null);
    }

    /** Solicita chave; validação, posse e registro DICT são responsabilidade do parceiro. */
    @PostMapping("/accounts/{id}/pix-keys")
    @PreAuthorize("hasAuthority('SCOPE_accounts/write')")
    @io.swagger.v3.oas.annotations.Operation(summary = "Solicitar registro de chave Pix")
    public Map<String, Object> key(
            @PathVariable String id,
            @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody PixKey body) {
        return bank.execute(
                tenants.resolve(http.getHeader("X-Merchant-Id")),
                Operation.CREATE_KEY,
                id,
                key,
                body);
    }

    /** Remove chave somente após validação de vínculo no parceiro. */
    @DeleteMapping("/accounts/{id}/pix-keys/{keyId}")
    @PreAuthorize("hasAuthority('SCOPE_accounts/write')")
    @io.swagger.v3.oas.annotations.Operation(summary = "Excluir chave Pix")
    public Map<String, Object> delete(
            @PathVariable String id,
            @PathVariable String keyId,
            @RequestHeader("Idempotency-Key") String key) {
        return bank.execute(
                tenants.resolve(http.getHeader("X-Merchant-Id")),
                Operation.DELETE_KEY,
                id,
                key,
                Map.of("key_id", keyId));
    }
}
