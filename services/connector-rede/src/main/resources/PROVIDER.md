# Rede: integração externa

Configuração: `provider.yml`, importado automaticamente por `application.yml`.
Autenticação: **OAuth2 client_credentials; client-id=PV e client-secret=chave de integração**.

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
Escopo Cognito: `connectors-rede/execute`. A instância fica vinculada ao UUID interno
`REDE_TENANT_ID`; outro estabelecimento recebe 403 antes de acessar a adquirente.
Para várias afiliações, execute instâncias isoladas por credencial ou implemente um resolvedor
por tenant em Secrets Manager; não compartilhe a mesma credencial silenciosamente.

O status de resposta é **nativo**. HTTP 200 da InPay não significa autorização aprovada.
`uncertain=true` exige consulta/recuperação operacional. Não há retry de cobrança nem fallback
para outra adquirente. A reserva idempotente sobrevive a timeout e reinício. Mantenha operationId;
use uma chave nova apenas para um novo comando financeiro deliberado.

## Variáveis

- `REDE_ENABLED`: propriedade `enabled`; padrão `false`.
- `REDE_TENANT_ID`: propriedade `tenant-id`; padrão `vazio`.
- `REDE_BASE_URL`: propriedade `base-url`; padrão `https://sandbox-erede.useredecloud.com.br`.
- `REDE_QUERY_URL`: propriedade `query-url`; padrão `https://sandbox-erede.useredecloud.com.br`.
- `REDE_TOKEN_URL`: propriedade `token-url`; padrão `https://rl7-sandbox-api.useredecloud.com.br/oauth2/token`.
- `REDE_MERCHANT_ID`: propriedade `merchant-id`; padrão `vazio`.
- `REDE_MERCHANT_KEY`: propriedade `merchant-key`; padrão `vazio`.
- `REDE_CLIENT_ID`: propriedade `client-id`; padrão `vazio`.
- `REDE_CLIENT_SECRET`: propriedade `client-secret`; padrão `vazio`.
- `REDE_ACCESS_TOKEN`: propriedade `access-token`; padrão `vazio`.
- `REDE_SECRET_KEY`: propriedade `secret-key`; padrão `vazio`.
- `REDE_APP_ID`: propriedade `app-id`; padrão `vazio`.
- `REDE_APP_KEY`: propriedade `app-key`; padrão `vazio`.
- `REDE_API_VERSION`: propriedade `api-version`; padrão `2021-03-22`.
- `REDE_HOST`: propriedade `host`; padrão `vazio`.
- `REDE_CHANNEL`: propriedade `channel`; padrão `vazio`.
- `REDE_KEY_STORE`: propriedade `key-store`; padrão `vazio`.
- `REDE_KEY_STORE_PASSWORD`: propriedade `key-store-password`; padrão `vazio`.
- `REDE_CONNECT_TIMEOUT`: propriedade `connect-timeout`; padrão `5s`.
- `REDE_READ_TIMEOUT`: propriedade `read-timeout`; padrão `30s`.
- `REDE_ALLOW_SENSITIVE_CARD_DATA`: propriedade `allow-sensitive-card-data`; padrão `false`.
- `REDE_BRAZIL_ENABLED`: propriedade `brazil-enabled`; padrão `false`.

Dados de cartão aberto/CVV/criptograma são bloqueados por padrão; o suporte a esses campos
exige `REDE_ALLOW_SENSITIVE_CARD_DATA=true` somente em ambiente PCI apropriado.
Esses campos nunca entram em journal, resposta ou log do connector; também são removidos da
impressão idempotente. Para diferenciar instrumentos use tokens persistentes do provedor.
Aliases `ptok_` simulados são rejeitados. O cofre local não converte tokens entre adquirentes.

Documentação oficial consultada: https://developer.userede.com.br/files/traducoes/erede/e-rede-24032026.pdf

A integração HTTP tem testes locais de contrato; homologação externa requer suas credenciais.

## Ambientes

O padrão é sandbox/homologação, com integração desabilitada. Para produção use
`SPRING_PROFILES_ACTIVE=aws,provider-production`, credenciais correspondentes e
URLs de produção do arquivo `application-provider-production.yml`. Não reutilize
variáveis BASE_URL/QUERY_URL/TOKEN_URL do sandbox. O Docker Compose principal é para desenvolvimento.

Usa endpoints transacionais v2 e OAuth2. O tutorial oficial consultado contém rótulos divergentes para a URL OAuth de produção; `REDE_TOKEN_URL` é obrigatório no perfil de produção e deve vir da coleção/credenciamento fornecido pela Rede. O exemplo usa DPAN de bandeira e criptograma, não um CardToken Cielo ou alias InPay.
