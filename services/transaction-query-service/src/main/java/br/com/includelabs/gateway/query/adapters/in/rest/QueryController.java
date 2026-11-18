package br.com.includelabs.gateway.query.adapters.in.rest;

import java.util.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.jdbc.core.JdbcTemplate;
import br.com.includelabs.gateway.security.*;
@RestController public class QueryController {
 private final JdbcTemplate jdbc;private final TenantContext tenants;
 public QueryController(JdbcTemplate jdbc,TenantContext tenants){this.jdbc=jdbc;this.tenants=tenants;}
 @GetMapping("/v1/transactions/{id}") @PreAuthorize("hasAuthority('SCOPE_transactions/read')")
 public Map<String,Object> get(@PathVariable UUID id,@RequestHeader(value="X-Merchant-Id",required=false)String tenant){
  var rows=jdbc.queryForList("SELECT id,amount,status,version FROM transaction_view WHERE id=? AND merchant_id=?",id,tenants.resolve(tenant));
  if(rows.isEmpty())throw new ApiErrors.NoSuchResource();return rows.getFirst();
 }
}
