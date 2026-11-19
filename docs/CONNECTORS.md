# Connectors externos InPay

Esta mudança implementa o núcleo transacional de cartões dos oito módulos: autorização,
captura, cancelamento/reversão, estorno e consulta. Os módulos continuam separados e usam
`libs/connector-runtime` somente para transporte, autenticação e journal durável.
A API externa recebe JSON nativo para conservar os campos opcionais/condicionais de cada
provedor. Não existe uma equivalência universal de tokens, parcelamento, 3DS ou split.

## APIs e autenticação

| Módulo | Produto/API implementado | Autenticação |
|---|---|---|
| connector-cielo | API E-commerce Cielo 3.0, `/1/sales` | MerchantId + MerchantKey |
| connector-rede | e.Rede v2 | OAuth2 client_credentials; PV/client-id e chave/client-secret |
| connector-getnet | Global/Regional API SEP | OAuth2, x-seller-id e x-transaction-channel-entry |
| connector-stone | Stone Online 4.0, `/v1/charges`, modalidade gateway | Basic SecretKey + Host do ambiente |
| connector-bin | Carat/e-SiTef REST Fiserv, pré-autorização v2/captura v1 | merchant_id + merchant_key + mTLS |
| connector-global-payments | Unified Payments / GP-API | nonce + SHA512, Bearer, X-GP-Version |
| connector-pagbank | Order / Charges | Bearer da conta |
| connector-safrapay | Gateway v2, pré-autorização | MerchantToken → JWT Bearer |

**Bin:** o cadastro deve habilitar mTLS. Não há fallback sem certificado e não foi implementada
a modalidade alternativa de assinatura JWT. Monte o PKCS12 em um caminho somente leitura,
configure BIN_KEY_STORE/BIN_KEY_STORE_PASSWORD e restrinja acesso ao arquivo.

**Global Payments:** a disponibilidade da API global para o contrato brasileiro não é presumida.
O connector permanece bloqueado até GLOBAL_PAYMENTS_BRAZIL_ENABLED=true, após confirmação
comercial/técnica de BR/BRL. Isso não implementa o protocolo legado Global Payments WS Brasil.

**Rede:** a migração para OAuth implica URLs transacionais v2. No perfil de produção a URL
de emissão do token deve ser configurada explicitamente conforme o onboarding; o tutorial
público consultado apresenta rótulos divergentes para o ambiente dessa URL.

## Arquivos de cada módulo

Em `services/connector-<nome>/src/main/resources`:

- `provider.yml`: todas as configurações do cliente HTTP; segredos somente via ambiente.
- `application-provider-production.yml`: seleção explícita das URLs de produção.
- `provider-examples/`: envelopes de autorização, captura, cancelamento e estorno.
- `PROVIDER.md`: variáveis, particularidades e referências oficiais.
- `db/migration/V3__external_operations.sql`: reserva e resultado das chamadas externas.

Os templates usam marcadores de dados e não são vendas prontas para envio. Substitua tokens,
dados do comprador e campos condicionais conforme sua afiliação. Stone e Rede podem exigir
PAN/DPAN e criptogramas: esses campos são bloqueados por padrão. O sinalizador
`*_ALLOW_SENSITIVE_CARD_DATA=true` é apenas uma opção técnica, não certificação PCI.
Não ative captura de corpos em proxies, APM, traces ou logs do ambiente PCI.

## Coleção completa Cielo

O módulo Cielo também expõe as 20 operações distintas da coleção oficial: vendas, consultas, captura/cancelamento, recorrência, tokenização, Zero Auth e BIN. Consulte [cobertura, exemplos e resultados de sandbox](CIELO_COLLECTION_COVERAGE.md).

## Testar em sandbox

O Docker Compose mantém todos os connectors externos desabilitados por padrão. Para Cielo,
por exemplo, adicione ao `.env` (não versionado):

```dotenv
CIELO_ENABLED=true
CIELO_TENANT_ID=11111111-1111-1111-1111-111111111111
CIELO_MERCHANT_ID=<merchant-id-sandbox>
CIELO_MERCHANT_KEY=<merchant-key-sandbox>
```

```bash
docker compose up --build -d connector-cielo
```

Swagger: `http://localhost:8101/swagger-ui/index.html`. Os endpoints externos também funcionam
com o perfil local quando explicitamente habilitados; o endpoint legado `/internal/v1/authorizations`
continua simulado nesse perfil. Fora de local, o legado usa o adaptador externo e exige `providerPayload`
com valor correspondente ao campo `amount` da InPay.

```bash
curl -u developer:local-only-change-me \
  -H 'Content-Type: application/json' \
  -H 'X-Merchant-Id: 11111111-1111-1111-1111-111111111111' \
  --data-binary @services/connector-cielo/src/main/resources/provider-examples/authorize.json \
  http://localhost:8101/internal/v1/provider-payments
```

A senha acima vale apenas para o perfil local padrão. Em AWS, use um access token Cognito válido
com o escopo `connectors-<nome>/execute` e o mapeamento de tenant configurado no serviço.
Cognito autentica módulos da InPay; as credenciais da adquirente são independentes.
Cada instância externa é vinculada a **uma afiliação e um UUID de lojista**. Outra identidade
é rejeitada antes de qualquer chamada HTTP. Para múltiplas afiliações, execute instâncias isoladas;
um resolvedor multi-tenant de credenciais/vault não faz parte desta mudança.

| Operação | Endpoint InPay |
|---|---|
| Autorizar/criar cobrança | POST `/internal/v1/provider-payments` |
| Capturar | POST `/internal/v1/provider-payments/{providerId}/CAPTURE` |
| Cancelar/reverter | POST `/internal/v1/provider-payments/{providerId}/CANCEL` |
| Estornar | POST `/internal/v1/provider-payments/{providerId}/REFUND` |
| Consultar | GET `/internal/v1/provider-payments/{providerId}` |

Os POSTs recebem `{ "operationId": "UUID", "payload": { ...JSON nativo... } }`.
Use um novo UUID para cada comando financeiro distinto. O mesmo UUID deve acompanhar os
reenvios do mesmo comando. O `providerId` é PaymentId/TID/charge-id/NIT, conforme o provedor;
no PagBank é o CHAR da cobrança, não o ORDE do pedido. A integração PagBank aceita uma charge
por operação para evitar que um pedido multimeios seja reduzido a um único resultado.

HTTP 200 da InPay significa que há um resultado para inspecionar, **não aprovação financeira**.
`status` é nativo do provedor, `code` é o código mínimo retornado, `httpStatus` é o HTTP externo
(0 se não houve resposta completa). Corpo bruto, cartão e credenciais não são devolvidos.

## Idempotência e falhas

1. A InPay valida identidade, configuração e contrato; autentica o connector.
2. PostgreSQL reserva `(merchant_id, operation_id)` em transação curta com commit próprio.
3. A chamada financeira ocorre fora da transação do banco, sem retry automático.
4. O resultado mínimo é persistido em nova transação. Reenvios retornam o resultado salvo.

Timeout, HTTP 408/429/5xx ou resposta inválida geram `uncertain=true`. Se o processo cair após a
reserva, a chave continua reservada como UNKNOWN. Não reenvie com uma chave nova apenas para
contornar esse estado. Consulte a adquirente; se não houver providerId, use a referência do pedido
no portal/API do provedor e faça recuperação operacional. A resolução automática por referência,
a reconciliação e a atualização da saga do Payment Core ainda não estão implementadas.

PAN, CVV, trilhas e criptogramas são removidos **antes** de calcular a impressão idempotente.
Esses campos efêmeros não diferenciam reenvios; tokens persistentes e o restante do comando sim.
Nenhum payload bruto é persistido. Após 401, o cache de token é invalidado, sem repetição da venda.
OAuth é reutilizado até pouco antes da expiração; falta de TTL impede cache prolongado.

## Produção

Use `SPRING_PROFILES_ACTIVE=aws,provider-production`, URLs/credenciais desse ambiente,
PostgreSQL durável, secrets externos e homologação contratual. O Compose principal é para
**desenvolvimento**, não implantação produtiva. Stone requer a opção JVM
`-Djdk.httpclient.allowRestrictedHeaders=host`, já incluída no Dockerfile.

Esta entrega não implementa todo o catálogo comercial das adquirentes (por exemplo onboarding,
chargebacks, relatórios de liquidação, Pix/boleto, tokenização/vault ou a jornada interativa 3DS).
Campos 3DS já obtidos externamente podem ser encaminhados no JSON nativo, conforme o contrato.
O token-service local continua simulado; aliases `ptok_` não são aceitos pelas APIs externas.

## Referências oficiais consultadas

- Cielo: https://docs.cielo.com.br/ecommerce-cielo/reference/sobre-a-api
- Cielo tokenizado: https://docs.cielo.com.br/ecommerce-cielo/reference/cartao-tokenizado-api
- Rede OAuth/v2: https://developer.userede.com.br/files/erede/oauth-faq.pdf
- Rede contratos: https://developer.userede.com.br/files/traducoes/erede/e-rede-24032026.pdf
- Getnet SEP: https://docs.globalgetnet.com/en/products/online-payments/regional-api?doc=create-pre-authorized-payment
- Stone Online: https://online.stone.com.br/reference/overview-da-api
- Stone autenticação: https://online.stone.com.br/reference/overview-da-autentica%C3%A7%C3%A3o
- Bin pré-autorização: https://dev.ecomm-bin.fiserv.com.br/docs/e-sitef/pre-autorizacao/
- Bin captura: https://dev.ecomm-bin.fiserv.com.br/docs/e-sitef/pre-autorizacao-rest-capture/
- Bin mTLS/cancelamento: https://dev.ecomm-bin.fiserv.com.br/docs/e-sitef/cancelamento/
- Global Payments autenticação: https://developer.globalpayments.com/api/definitions/access-tokens
- Global Payments transações: https://developer.globalpayments.com/api/transactions
- PagBank: https://developer.pagbank.com.br/reference/criar-pedido
- Safrapay: https://developers.safrapay.com.br/autenticacao
- Safrapay cobranças: https://developers.safrapay.com.br/cobrancas
