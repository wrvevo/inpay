# Jornadas e limites da implementação — 22/11/2026

Abra [inpay-journeys.drawio](inpay-journeys.drawio) no diagrams.net. São 12 páginas editáveis:
visão geral, Pix, crédito, débito, transferência, boleto, cancelamento, recorrência,
conciliação, split, agendamento e cobrança. Os diagramas são uma arquitetura de destino;
os blocos pontilhados e esta matriz identificam o que ainda precisa de implementação.

## Separação de responsabilidades

- **Gateway:** recebe pedidos, autentica tenant, reserva idempotência e coordena pagamentos.
- **Accounts:** configura instituições e referências de credenciais; operações financeiras
  dependem do contrato e da instituição parceira. Um cadastro não cria uma conta bancária.
- **Marketplace:** regras comerciais, divisão de valores, repasses e conciliação.
- **Credits:** benefícios/gift cards e créditos segregados, sem misturar com liquidação bancária.
- **Conectores:** aplicações separadas, contrato REST interno e persistência própria. Apenas
  elas possuem segredos do provedor. Nenhum JAR de conector entra no classpath do Gateway.

REST é usado para comandos/consultas com resposta imediata. Outbox e alteração de estado são
atômicos no PostgreSQL; eventos vão para `inpay.payment.journeys.v1`, particionados por jornada.
Kafka é a ponte interna durável. Webhooks são a fronteira com bancos/lojistas, não uma sequência
de chamadas HTTP que substitui a durabilidade do broker. Consumidores precisam deduplicar por
`eventId`; ordenação vale por partição, não globalmente. Nunca publicar PAN/CVV em eventos.

A publicação Kafka utiliza a infraestrutura existente. O E2E novo desabilita consumidores e
não comprova entrega Kafka, DLQ ou callbacks. Consumidores de settlement, notificações externas,
inbox e automação completa dos fluxos deste documento ainda precisam ser conectados e testados.
Callback externo deve passar por autenticação/assinatura e consulta ao provedor antes de liberar
saldo/produto. Não confiar em status enviado pelo navegador ou em um webhook sem verificação.

## Matriz real de cobertura

| Jornada | Implementado nesta base | Falta para homologação completa |
|---|---|---|
| Loja / Gateway / Cielo crédito | Compra, consulta e cancelamento reais no sandbox, processos separados | Tokenização validada, 3DS e cenários de falha completos, produção |
| Loja Pix, débito, boleto e transferência | Checkout e ciclo local com simulador | Habilitação e E2E bancário de cada meio; autenticação de débito |
| Cielo e-Commerce | 20 operações nativas distintas da coleção no conector; parâmetros nativos opcionais | Cobertura de cada variante comercial e erros; ver relatório de testes |
| Cielo Pix Cielo2 | Payload nativo e configuração | Sandbox oficial não disponibiliza novo Pix; habilitação comercial e teste controlado |
| C6 / bancos | Catálogo e referências por tenant no Accounts e painel | Aplicações conectoras bancárias, OpenAPI autorizado, mTLS/OAuth, credenciais e testes |
| Recorrência | Operações nativas Cielo; geração de faturas no Billing | Orquestração integral da loja, agendamento durável, retries por regra de negócio |
| Conciliação | Consulta de pagamento e outbox da nova jornada | Arquivos/extratos, taxas, liquidação, divergências e fechamento financeiro |
| Split | Prévia comercial existente e desenho de fluxo | Split financeiro contratado, recebedores, estornos proporcionais, repasses efetivos |
| Agendamento/cobrança | Base Billing e arquitetura | Execução end-to-end, consentimentos, comunicação SMS/e-mail/WhatsApp |
| Webhooks | Infraestrutura/base existente e modelo | Ligar consumidor novo, validação bancária, inbox, entrega ao lojista e DLQ |

`/gateway/v1/journeys` é a nova API do laboratório. Não modifica silenciosamente o comportamento
anterior de `/gateway/v1/orders`. A convergência destes contratos públicos ainda é necessária
antes de disponibilizar a integração como produto de produção.

## Bancos e acesso à documentação

Os 12 registros em `accounts-service/src/main/resources/banks` estão explicitamente
`NOT_IMPLEMENTED`; nenhuma opção do painel ativa operações. Referências de certificado são
metadados e ainda não são resolvidas por um adaptador. Cadastro não significa homologação.

- C6: portal oficial retornou HTTP 403 nesta consulta. Faltam documentação/OpenAPI acessível,
  credenciais sandbox e certificado/escopos exigidos pelo produto contratado.
- Itaú, Santander, Bradesco, Inter: validar produto, habilitação da conta, OAuth/mTLS,
  certificados e contrato antes de implementar endpoints específicos.
- Nubank: NuPay checkout não equivale a API genérica de contas/BaaS; não presumir equivalência.
- PAN, BB, Caixa, BTG, Sicoob e Sicredi: inventário preparado, sem afirmar cobertura de APIs.

Fontes oficiais para homologação: [C6](https://developers.c6bank.com.br/apis),
[Itaú](https://devportal.itau.com.br/como-se-conectar-as-apis-itau),
[Santander](https://developer.santander.com.br/), [Bradesco](https://api.bradesco/),
[Inter](https://developers.inter.co/docs/introducao/nossas-apis),
[NuPay](https://docs.nupaybusiness.com.br/checkout/docs/openapi/index.html).
O Inter informa descontinuação do boleto V2; usar contrato atual, não copiar endpoint antigo.

## Escopo Cielo

A coleção e-Commerce não representa todo o portal Cielo. Split, Risk, Smart, Conecta,
Chargeback, Extrato, Tap e Pix presencial são produtos/contratos próprios e não foram
implementados integralmente nesta entrega. Uma MerchantKey e-Commerce não comprova acesso
a esses produtos. A matriz deve ser ampliada por operação e versão com os contratos liberados.

Fontes: [coleção e-Commerce](https://docs.cielo.com.br/ecommerce-cielo/reference/collections-e-environments-cielo),
[Pix Cielo](https://docs.cielo.com.br/ecommerce-cielo/docs/pix-cielo),
[portal de produtos](https://docs.cielo.com.br/).
