# Sessão funcional local — 26/11/2026

Plano executado nesta máquina (16 GB de RAM, Windows). As aplicações subiram com o mesmo perfil `local`, as mesmas classes principais e as mesmas portas das run configurations do IntelliJ. O botão Run do IntelliJ não foi acionado: a interface da IDE não pôde ser operada daqui, então os JARs empacotados foram iniciados diretamente. Chrome foi aberto e as abas abaixo ficaram disponíveis.

A primeira tentativa de subir os 32 processos ao mesmo tempo esgotou a memória nativa da JVM (`insufficient memory` / falha ao carregar `jvm.dll`). A segunda subida foi em grupos e parou quando a RAM livre caiu para cerca de 1,7 GB.

## Plano

1. Infra: Postgres, Kafka, Prometheus e Grafana (`docker-compose.infra.yml`).
2. Simulador de jornada em `8120` e loja de exemplo em `8199`. A porta `8099` fica com o WireMock; por isso a loja não usa `8099`.
3. Gateway, Accounts, Credits, Marketplace, Billing, mock server e conectores bancários.
4. Adquirentes e demais serviços do gateway se ainda houver memória.
5. Abrir no Chrome Swagger, painel, loja, administração da loja, Grafana e o mock.
6. Consultar catálogos e executar `PIX_COB_POST` com `X-Mock: true` no conector e no gateway.
7. Confirmar os 280 stubs do WireMock.
8. Criar pedidos na loja para os meios habilitados e consultar o provedor.
9. Anotar falhas no Grafana.

## O que ficou no ar

| Superfície | Endereço | Resultado |
|---|---|---|
| Swagger do gateway | http://127.0.0.1:8084/swagger-ui/index.html | Página HTTP 200 |
| Swagger Accounts | http://127.0.0.1:8111/swagger-ui/index.html | HTTP 200 |
| Swagger Marketplace | http://127.0.0.1:8112/swagger-ui/index.html | HTTP 200 |
| Swagger Credits | http://127.0.0.1:8110/swagger-ui/index.html | HTTP 200 |
| Swagger Billing | http://127.0.0.1:8113/swagger-ui/index.html | HTTP 200 |
| Painel (interface) | http://127.0.0.1:5173/ | Vite no ar |
| API do painel | http://127.0.0.1:3080/healthz | HTTP 200. `GET /` responde 404: essa porta é a API, não a interface |
| Loja de exemplo | http://127.0.0.1:8199/ | HTTP 200, 27 meios |
| Painel da loja | http://127.0.0.1:8199/admin | HTTP 200, senha local `inpay@123` |
| Mock server | http://127.0.0.1:8098/actuator/health | UP |
| Stubs WireMock | http://127.0.0.1:8099/__admin/mappings | 280 mappings |
| Grafana | http://127.0.0.1:3000/ | Login HTTP 200. Admin local `admin` / `inpay@123` |
| Simulador | http://127.0.0.1:8120/health | `simulation: true` |
| C6, Itaú, Santander, BB, Bradesco, Caixa, Banrisul | 8121–8127 | Health UP |

Não subiram, por falta de memória: merchant, links, checkout, routing, token, fraud, webhook, reconciliation, notification, query e os conectores Cielo, Rede, Getnet, Stone, Bin, Global Payments, PagBank e SafraPay.

## Módulos

| Teste | Resultado |
|---|---|
| `GET /accounts/v1/banks` | 200, catálogo com C6 e demais bancos |
| `GET /marketplace/v1/recipients` | 200, lista vazia |
| `GET /credits/v1/admin` | 200, sem contas nem pedidos |
| `GET /gateway/v1/providers` | 200, inventário com bancos e adquirentes, `routing_enabled=false` |
| `GET /billing/v1/invoices` | 405. A rota não aceita GET; o teste é que usou o método errado |

## Conectores e mock

`X-Mock: true` em `PIX_COB_POST`, direto no conector e também pelo gateway:

| Banco | Conector | Gateway |
|---|---|---|
| Itaú | 200, `pixCopiaECola=000201mocked-itau` | 200 |
| Santander | 200, corpo mockado | 200 |
| Banco do Brasil | 200, stub `000201mocked` | 200 |
| Bradesco | 200, stub | 200 |
| Caixa | 200, stub | 200 |
| Banrisul | 200, stub | 200 |
| C6 | tempo esgotado na chamada direta | 503 `PROVIDER_UNAVAILABLE` |

O WireMock carregou 280 stubs. A primeira subida do mock server quebrava no Windows com `InvalidPathException` em `nested:\...\jar` porque o WireMock lia o classpath do JAR aninhado do Spring Boot. O servidor passou a ler `libs/bank-connector/src/main/resources/wiremock` no disco.

O simulador também quebrava no Windows ao abrir `/tmp/inpay-simulator.sqlite`. O arquivo padrão passou a usar o diretório temporário do sistema.

## Pedidos na loja

Os 27 meios aparecem no checkout. A primeira leva de pedidos ficou `UNKNOWN` com diagnóstico `CONNECTOR_HTTP_401`: o gateway não enviava o Basic completo ao simulador. O gateway foi reiniciado com `INPAY_JOURNEY_AUTHORIZATION` correto. Depois disso:

| Pedido | Criação | Depois de consultar o provedor |
|---|---|---|
| Pix | PENDING, QR `SIMULADO-NAO-PAGAVEL` | PAID |
| Cartão de crédito | PAID | PAID |
| Boleto | PENDING | PAID |

São liquidações só do simulador. Nenhum banco real foi chamado.

## Grafana

Duas anotações em http://127.0.0.1:3000 , tag `functional-test`:

- a sessão automática, com as falhas de memória, o 405 de billing e o C6;
- o reteste da jornada, com Pix/cartão/boleto pagos no simulador.

## O que não foi concluído

- Os oito conectores de adquirente não foram exercitados: não houve RAM para mantê-los junto com o gateway e os bancos.
- O C6 com `X-Mock: true` não respondeu dentro do tempo desta sessão.
- Não há uma instalação Magento Open Source separada. A loja acessível é a vitrine de exemplo na porta 8199, com o módulo em `examples/magento` para copiar numa loja Magento.
