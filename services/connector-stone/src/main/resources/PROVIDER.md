# Stone: integração externa

Configuração: `provider.yml`, importado automaticamente por `application.yml`.
Autenticação: **HTTP Basic secret-key com senha vazia; Host obrigatório para gateway**.

Os endpoints `/internal/v1/provider-payments` recebem um envelope `operationId` + `payload`.
`payload` é o JSON nativo da API: campos opcionais documentados são preservados (cliente,
parcelamento, 3DS, recorrência, split quando disponível). Não é um contrato unificado de cartão.
Exemplos em `provider-examples/` são templates: substitua os marcadores e complete os campos
condicionais exigidos pelo seu contrato. Nenhuma credencial de teste foi incorporada.

- POST `/internal/v1/provider-payments`: autorização/criação da cobrança.
- POST `/internal/v1/provider-payments/{id}/CAPTURE`: captura.
- POST `/internal/v1/provider-payments/{id}/CANCEL`: cancelamento/reversão.
- POST `/internal/v1/provider-payments/{id}/REFUND`: estorno.
- GET `/internal/v1/provider-payments/{id}`: consulta.

Use o ID **da cobrança/transação** retornado em `providerId`, não o ID do pedido.
Escopo Cognito: `connectors-stone/execute`. A instância fica vinculada ao UUID interno
`STONE_TENANT_ID`; outro estabelecimento recebe 403 antes de acessar a adquirente.
Para várias afiliações, execute instâncias isoladas por credencial ou implemente um resolvedor
por tenant em Secrets Manager; não compartilhe a mesma credencial silenciosamente.

O status de resposta é **nativo**. HTTP 200 da InPay não significa autorização aprovada.
`uncertain=true` exige consulta/recuperação operacional. Não há retry de cobrança nem fallback
para outra adquirente. A reserva idempotente sobrevive a timeout e reinício. Mantenha operationId;
use uma chave nova apenas para um novo comando financeiro deliberado.

## Variáveis

- `STONE_ENABLED`: propriedade `enabled`; padrão `false`.
- `STONE_TENANT_ID`: propriedade `tenant-id`; padrão `vazio`.
- `STONE_BASE_URL`: propriedade `base-url`; padrão `https://payments.stone.com.br`.
- `STONE_QUERY_URL`: propriedade `query-url`; padrão `https://payments.stone.com.br`.
- `STONE_TOKEN_URL`: propriedade `token-url`; padrão `vazio`.
- `STONE_MERCHANT_ID`: propriedade `merchant-id`; padrão `vazio`.
- `STONE_MERCHANT_KEY`: propriedade `merchant-key`; padrão `vazio`.
- `STONE_CLIENT_ID`: propriedade `client-id`; padrão `vazio`.
- `STONE_CLIENT_SECRET`: propriedade `client-secret`; padrão `vazio`.
- `STONE_ACCESS_TOKEN`: propriedade `access-token`; padrão `vazio`.
- `STONE_SECRET_KEY`: propriedade `secret-key`; padrão `vazio`.
- `STONE_APP_ID`: propriedade `app-id`; padrão `vazio`.
- `STONE_APP_KEY`: propriedade `app-key`; padrão `vazio`.
- `STONE_API_VERSION`: propriedade `api-version`; padrão `2021-03-22`.
- `STONE_HOST`: propriedade `host`; padrão `sdx-ecommerce-payments.stone.com.br`.
- `STONE_CHANNEL`: propriedade `channel`; padrão `vazio`.
- `STONE_KEY_STORE`: propriedade `key-store`; padrão `vazio`.
- `STONE_KEY_STORE_PASSWORD`: propriedade `key-store-password`; padrão `vazio`.
- `STONE_CONNECT_TIMEOUT`: propriedade `connect-timeout`; padrão `5s`.
- `STONE_READ_TIMEOUT`: propriedade `read-timeout`; padrão `30s`.
- `STONE_ALLOW_SENSITIVE_CARD_DATA`: propriedade `allow-sensitive-card-data`; padrão `false`.
- `STONE_BRAZIL_ENABLED`: propriedade `brazil-enabled`; padrão `false`.

Dados de cartão aberto/CVV/criptograma são bloqueados por padrão; o suporte a esses campos
exige `STONE_ALLOW_SENSITIVE_CARD_DATA=true` somente em ambiente PCI apropriado.
Esses campos nunca entram em journal, resposta ou log do connector; também são removidos da
impressão idempotente. Para diferenciar instrumentos use tokens persistentes do provedor.
Aliases `ptok_` simulados são rejeitados. O cofre local não converte tokens entre adquirentes.

Documentação oficial consultada: https://online.stone.com.br/reference/post_charges

A integração HTTP tem testes locais de contrato; homologação externa requer suas credenciais.

## Ambientes

O padrão é sandbox/homologação, com integração desabilitada. Para produção use
`SPRING_PROFILES_ACTIVE=aws,provider-production`, credenciais correspondentes e
URLs de produção do arquivo `application-provider-production.yml`. Não reutilize
variáveis BASE_URL/QUERY_URL/TOKEN_URL do sandbox. O Docker Compose principal é para desenvolvimento.

Para executar fora do Docker, inclua `-Djdk.httpclient.allowRestrictedHeaders=host` na JVM. O Host define o ambiente Stone e deve corresponder às credenciais. O exemplo manual exige ambiente PCI e os campos condicionais da bandeira.
