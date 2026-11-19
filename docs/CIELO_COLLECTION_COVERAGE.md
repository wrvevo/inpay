# Cobertura da coleção oficial Cielo

Origem: https://www.postman.com/collections/7313fe78130211f5f009

Snapshot SHA-256: `ad5c99f513ae6354de478f07b02456768234068d55b601ddb260f9ce40c09dca`. Obtido em 19/11/2026.

40 requisições mapeadas para 20 operações distintas. Variantes POST /1/sales preservam seus campos nativos. Não executamos scripts Postman nem importamos credenciais da coleção. Quatro exemplos tinham sintaxe JSON inválida e foram corrigidos; datas e dados de teste foram substituídos.

| Grupo | Requisição Postman | Operação InPay | Método externo |
|---|---|---|---|
| MP - Cartão de crédito | Authorize - CreditCard - Mínimo | `CREATE_SALE` | `POST /1/sales` |
| MP - Cartão de crédito | Authorize - CreditCard - Autenticated | `CREATE_SALE` | `POST /1/sales` |
| MP - Cartão de crédito | Authorize - CreditCard - Completo | `CREATE_SALE` | `POST /1/sales` |
| MP - Cartão de débito | Authorize - DebitCard | `CREATE_SALE` | `POST /1/sales` |
| MP - Boleto | Authorize - Boleto - Sem Registro e Registrado | `CREATE_SALE` | `POST /1/sales` |
| MP - Transferência Eletrônica | Authorize - ElectronicTransfer | `CREATE_SALE` | `POST /1/sales` |
| Antifraud Gateway | Authorize - CreditCard - FraudAnalysis | `CREATE_SALE` | `POST /1/sales` |
| Consulta - Captura - Cancelamento/Cancelamento de transações | Void - By PaymentID | `VOID_PAYMENT` | `PUT /1/sales/{resourceId}/void` |
| Consulta - Captura - Cancelamento/Cancelamento de transações | Void - By MerchantOrderId | `VOID_ORDER` | `PUT /1/sales/orderId/{resourceId}/void` |
| Consulta - Captura - Cancelamento/Cancelamento de transações | Void - By PaymentID - Parcial | `VOID_PAYMENT` | `PUT /1/sales/{resourceId}/void?amount={amount}` |
| Consulta - Captura - Cancelamento/Captura de transações | Capture - By PaymentID | `CAPTURE_PAYMENT` | `PUT /1/sales/{resourceId}/capture` |
| Consulta - Captura - Cancelamento/Captura de transações | Capture - By PaymentID - Parcial | `CAPTURE_PAYMENT` | `PUT /1/sales/{resourceId}/capture?amount={amount}` |
| Consulta - Captura - Cancelamento/Consulta de Transações | Get | `GET_PAYMENT` | `GET /1/sales/{resourceId}` |
| Recorrência | Authorize - Recurrent - CreditCard | `CREATE_SALE` | `POST /1/sales` |
| Recorrência | Get - RecurrentPayment | `GET_RECURRENT` | `GET /1/RecurrentPayment/{resourceId}` |
| Recorrência | Update - Recurrent - Customer | `UPDATE_RECURRENT_CUSTOMER` | `PUT /1/RecurrentPayment/{resourceId}/Customer` |
| Recorrência | Update - Recurrent - Interval | `UPDATE_RECURRENT_INTERVAL` | `PUT /1/RecurrentPayment/{resourceId}/Interval` |
| Recorrência | Update - Recurrent - EndDate | `UPDATE_RECURRENT_END_DATE` | `PUT /1/RecurrentPayment/{resourceId}/EndDate` |
| Recorrência | Update - Recurrent - RecurrencyDay | `UPDATE_RECURRENT_DAY` | `PUT /1/RecurrentPayment/{resourceId}/RecurrencyDay` |
| Recorrência | Update - Recurrent - Reactivate | `REACTIVATE_RECURRENT` | `PUT /1/RecurrentPayment/{resourceId}/Reactivate` |
| Recorrência | Update - Recurrent - Deactivate | `DEACTIVATE_RECURRENT` | `PUT /1/RecurrentPayment/{resourceId}/Deactivate` |
| Recorrência | Update - Recurrent - Installments | `UPDATE_RECURRENT_INSTALLMENTS` | `PUT /1/RecurrentPayment/{resourceId}/Installments` |
| Recorrência | Update - Recurrent - Payment | `UPDATE_RECURRENT_PAYMENT` | `PUT /1/RecurrentPayment/{resourceId}/Payment` |
| Recorrência | Update - Recurrent - NextPaymentDate | `UPDATE_RECURRENT_NEXT_DATE` | `PUT /1/RecurrentPayment/{resourceId}/NextPaymentDate` |
| Recorrência | Update - Recurrent - Amount | `UPDATE_RECURRENT_AMOUNT` | `PUT /1/RecurrentPayment/{resourceId}/Amount` |
| Tokenização | Authorize - CreditCard - Saving Card | `CREATE_SALE` | `POST /1/sales` |
| Tokenização | Authorize - CreditCard - Using Token 1.5 | `CREATE_SALE` | `POST /1/sales` |
| Tokenização | Authorize - CreditCard - Using Token | `CREATE_SALE` | `POST /1/sales` |
| Tokenização | Card - Create | `CREATE_CARD` | `POST /1/card` |
| Tokenização | Card - Get | `GET_CARD` | `GET /1/card/{resourceId}` |
| Wallet | Authorize - CreditCard - VisaCheckout - Descriptografia | `CREATE_SALE` | `POST /1/sales` |
| Wallet | Authorize - CreditCard - VisaCheckout - Envio de cartão | `CREATE_SALE` | `POST /1/sales` |
| Wallet | Authorize - CreditCard - ApplePay - Descriptografia | `CREATE_SALE` | `POST /1/sales` |
| Wallet | Authorize - CreditCard - ApplePay - Envio de cartão | `CREATE_SALE` | `POST /1/sales` |
| Wallet | Authorize - CreditCard - MasterPass - Envio de cartão | `CREATE_SALE` | `POST /1/sales` |
| AVS | Authorize - CreditCard - With AVS | `CREATE_SALE` | `POST /1/sales` |
| Zero Auth | ZeroAuth - CardToken | `ZERO_AUTH` | `POST /1/zeroauth` |
| Zero Auth | ZeroAuth - Validate With AVS | `ZERO_AUTH` | `POST /1/zeroauth` |
| Zero Auth | ZeroAuth - Validate | `ZERO_AUTH` | `POST /1/zeroauth` |
| Consulta Bins | Bin Query | `QUERY_BIN` | `GET /1/cardBin/{resourceId}` |

## Configuração e execução

As propriedades estão em `services/connector-cielo/src/main/resources/provider.yml` e são resolvidas por variáveis de ambiente. Configure as credenciais de homologação somente no `.env` ignorado pelo Git ou em um gerenciador de segredos:

```dotenv
CIELO_ENABLED=true
CIELO_TENANT_ID=11111111-1111-1111-1111-111111111111
CIELO_MERCHANT_ID=<merchant-id-do-sandbox>
CIELO_MERCHANT_KEY=<merchant-key-do-sandbox>
CIELO_BASE_URL=https://apisandbox.cieloecommerce.cielo.com.br
CIELO_QUERY_URL=https://apiquerysandbox.cieloecommerce.cielo.com.br
CIELO_ALLOW_SENSITIVE_CARD_DATA=true
```

O último parâmetro permite receber o cartão de teste transitoriamente; não habilita armazenamento de PAN/CVV. Com Java 21 e Maven instalados, execute na raiz:

```bash
python3 scripts/test-cielo-sandbox.py
```

O script recusa URLs diferentes das duas URLs oficiais de sandbox. O teste inicia um PostgreSQL temporário e o Spring Boot, chama os endpoints HTTP autenticados do InPay e estes chamam a Cielo. Não exige Docker nem Kafka em execução. O PostgreSQL embarcado precisa de um usuário sem privilégios de root. Para ambiente com proxy, os argumentos JVM podem ser enviados após `--`, por exemplo `-- -DargLine="-Dhttps.proxyHost=proxy -Dhttps.proxyPort=8080"`.

O relatório sanitizado é gerado em `services/connector-cielo/target/cielo-sandbox-report.json`. O ciclo de autorização, consulta, captura e cancelamento contém asserções obrigatórias. Os produtos complementares são sondagens: seus códigos devem ser examinados no relatório, e a aprovação do teste não significa que todos estejam habilitados.

## Endpoints InPay

- Comandos: `POST /internal/v1/cielo/operations/{operation}`.
- Consultas: `GET /internal/v1/cielo/operations/{operation}?resourceId={id}`.
- Autorização: escopo Cognito `connectors-cielo/execute`, com tenant correspondente ao estabelecimento configurado. Basic Auth é restrito ao perfil local.
- Swagger: `/swagger-ui/index.html` no serviço connector-cielo; schema em `/v3/api-docs`.

Exemplo de captura em centavos:

```json
{
  "operationId": "8db17023-b648-407d-bcf8-376b5514b6b5",
  "resourceId": "<PaymentId retornado pela Cielo>",
  "amount": 1000
}
```

Envie esse corpo para `/internal/v1/cielo/operations/CAPTURE_PAYMENT`. Omitir `amount` solicita captura total. `CREATE_SALE`, `CREATE_CARD` e `ZERO_AUTH` recebem o corpo nativo em `payload`. Atualizações de recorrência recebem objetos (Customer/Payment), números (Interval/RecurrencyDay/Installments/Amount) ou strings de data ISO (EndDate/NextPaymentDate). Ativação/desativação não recebem `payload`. Todos os 40 exemplos sanitizados constam em `resources/cielo-collection/catalog.json`, incluindo o caminho InPay correspondente.

A resposta contém `httpStatus` da adquirente, `uncertain`, `replayed` e `data`. Verifique esses campos e o status financeiro nativo; HTTP 200 do InPay não significa aprovação da transação. Erros locais de contrato e autorização usam os códigos HTTP documentados no Swagger.

## Idempotência e limites

Comandos exigem `operationId` UUID. O journal PostgreSQL reserva a operação antes da chamada e impede reenvio com a mesma chave. Reutilizar a chave com outro comando causa conflito. Em timeout ou resposta ambígua, consulte o pagamento antes de qualquer nova tentativa; não há retry financeiro automático.

O journal guarda somente identificador, status, código e resultado técnico. Não persiste corpos, dados de cliente, PAN, CVV ou CardToken. Por isso, replays devolvem um resumo e não reproduzem o corpo original. Na criação de token, o consumidor deve guardar com segurança o CardToken recebido; repetir a chave não recupera um token cuja resposta se perdeu.

A integração cobre os contratos REST da coleção fornecida. Carteiras digitais, autenticação 3DS, AVS, antifraude externo e determinados meios de pagamento podem exigir credenciais, criptogramas e habilitações adicionais. A coleção não equivale a uma certificação de disponibilidade desses produtos no estabelecimento. O antifraude Cielo é diferente do módulo antifraude nativo do InPay.

Estes endpoints pertencem ao conector interno. O fluxo completo da API pública `/v1/payments`, a saga financeira e a iniciação de cartão pelo painel não são concluídos por esta mudança.

## Resultado observado em 19/11/2026, 14h13 (Brasília)

Build completo: **114 testes unitários/de contrato, zero falhas ou erros**. Perfil de integração:
**1 teste de ciclo de vida, zero falhas ou erros**, com sondagens complementares documentadas
abaixo. Evidência sanitizada versionada em [CIELO_SANDBOX_RESULT.json](CIELO_SANDBOX_RESULT.json).
Foram chamadas 19 das 20 operações distintas; GET_CARD depende da criação de token, que falhou.

| Fluxo | Resultado efetivamente observado |
|---|---|
| Crédito via API HTTP InPay | Autorização HTTP 201/status 1, consulta HTTP 200, captura status 2, cancelamento status 10 e consulta final status 10 |
| Idempotência | Repetição do mesmo operationId devolveu o resultado persistido |
| Captura/cancelamento parcial | HTTP 200, status 2 e 10, respectivamente, para R$ 4,00 de uma venda de R$ 10,00 |
| Cancelamento por pedido | HTTP 200/status 10 |
| Cartão recusado | HTTP 201/status 3, código 05; recusa financeira corretamente distinguida de falha HTTP |
| Recorrência | Criada com identificador; consulta e todas as 10 operações de alteração/ativação/desativação retornaram HTTP 200; encerrada desativada, pagamento inicial cancelado |
| Consulta BIN | HTTP 200, Status 00 |
| Débito | Criação HTTP 201/status 2; não representa homologação 3DS com navegador |
| Boleto | Criação HTTP 201/status 1; liquidação não foi simulada |
| Tokenização | HTTP 400; diagnóstico adicional no endpoint oficial retornou `CP900: Merchant was not found`. GET_CARD e pagamento por token não foram executados |
| Zero Auth | HTTP 200, `Valid=false`, ReturnCode 11 na execução final. Não aprovado |
| Transferência eletrônica | HTTP 400, código 119; não habilitada/validada por este teste |
| Wallets, AVS, antifraude Cielo e 3DS | Contratos e variantes cobertos no catálogo; não exercitados com credenciais/criptogramas reais desses produtos |

O primeiro cartão usado para autorização não atendia à validação necessária para a recorrência.
A execução final usa o cartão sintético `4111111111111111`, válido no algoritmo de Luhn,
**exclusivamente no sandbox**. Fonte da regra: https://docs.cielo.com.br/ecommerce-cielo/reference/credito-sandbox.

A resposta CP900 deve ser investigada com o suporte/onboarding Cielo para o MerchantId de
sandbox informado. Nenhum segredo consta neste relatório. A aprovação do crédito e os HTTP 200
de recorrência não removem as pendências dos demais produtos nem constituem homologação de produção.
