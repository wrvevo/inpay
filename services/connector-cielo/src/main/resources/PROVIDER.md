# Cielo: integração externa

Configuração: `provider.yml`, importado automaticamente por `application.yml`.
Autenticação: **MerchantId e MerchantKey**.

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
Escopo Cognito: `connectors-cielo/execute`. A instância fica vinculada ao UUID interno
`CIELO_TENANT_ID`; outro estabelecimento recebe 403 antes de acessar a adquirente.
Para várias afiliações, execute instâncias isoladas por credencial ou implemente um resolvedor
por tenant em Secrets Manager; não compartilhe a mesma credencial silenciosamente.

O status de resposta é **nativo**. HTTP 200 da InPay não significa autorização aprovada.
`uncertain=true` exige consulta/recuperação operacional. Não há retry de cobrança nem fallback
para outra adquirente. A reserva idempotente sobrevive a timeout e reinício. Mantenha operationId;
use uma chave nova apenas para um novo comando financeiro deliberado.

## Variáveis

- `CIELO_ENABLED`: propriedade `enabled`; padrão `false`.
- `CIELO_TENANT_ID`: propriedade `tenant-id`; padrão `vazio`.
- `CIELO_BASE_URL`: propriedade `base-url`; padrão `https://apisandbox.cieloecommerce.cielo.com.br`.
- `CIELO_QUERY_URL`: propriedade `query-url`; padrão `https://apiquerysandbox.cieloecommerce.cielo.com.br`.
- `CIELO_TOKEN_URL`: propriedade `token-url`; padrão `vazio`.
- `CIELO_MERCHANT_ID`: propriedade `merchant-id`; padrão `vazio`.
- `CIELO_MERCHANT_KEY`: propriedade `merchant-key`; padrão `vazio`.
- `CIELO_CLIENT_ID`: propriedade `client-id`; padrão `vazio`.
- `CIELO_CLIENT_SECRET`: propriedade `client-secret`; padrão `vazio`.
- `CIELO_ACCESS_TOKEN`: propriedade `access-token`; padrão `vazio`.
- `CIELO_SECRET_KEY`: propriedade `secret-key`; padrão `vazio`.
- `CIELO_APP_ID`: propriedade `app-id`; padrão `vazio`.
- `CIELO_APP_KEY`: propriedade `app-key`; padrão `vazio`.
- `CIELO_API_VERSION`: propriedade `api-version`; padrão `2021-03-22`.
- `CIELO_HOST`: propriedade `host`; padrão `vazio`.
- `CIELO_CHANNEL`: propriedade `channel`; padrão `vazio`.
- `CIELO_KEY_STORE`: propriedade `key-store`; padrão `vazio`.
- `CIELO_KEY_STORE_PASSWORD`: propriedade `key-store-password`; padrão `vazio`.
- `CIELO_CONNECT_TIMEOUT`: propriedade `connect-timeout`; padrão `5s`.
- `CIELO_READ_TIMEOUT`: propriedade `read-timeout`; padrão `30s`.
- `CIELO_ALLOW_SENSITIVE_CARD_DATA`: propriedade `allow-sensitive-card-data`; padrão `false`.
- `CIELO_BRAZIL_ENABLED`: propriedade `brazil-enabled`; padrão `false`.

Dados de cartão aberto/CVV/criptograma são bloqueados por padrão; o suporte a esses campos
exige `CIELO_ALLOW_SENSITIVE_CARD_DATA=true` somente em ambiente PCI apropriado.
Esses campos nunca entram em journal, resposta ou log do connector; também são removidos da
impressão idempotente. Para diferenciar instrumentos use tokens persistentes do provedor.
Aliases `ptok_` simulados são rejeitados. O cofre local não converte tokens entre adquirentes.

Documentação oficial consultada: https://docs.cielo.com.br/ecommerce-cielo/reference/cartao-tokenizado-api

A integração HTTP tem testes locais de contrato; homologação externa requer suas credenciais.

## Ambientes

O padrão é sandbox/homologação, com integração desabilitada. Para produção use
`SPRING_PROFILES_ACTIVE=aws,provider-production`, credenciais correspondentes e
URLs de produção do arquivo `application-provider-production.yml`. Não reutilize
variáveis BASE_URL/QUERY_URL/TOKEN_URL do sandbox. O Docker Compose principal é para desenvolvimento.
