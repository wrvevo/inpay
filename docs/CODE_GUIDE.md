# Guia de leitura e manutenção do InPay

## Caminho sugerido para entender um módulo

1. Leia o README do módulo para distinguir capacidade implementada de ponto de extensão.
2. Abra o controller em `adapters/in/rest`: ele recebe HTTP, exige scope, resolve o lojista e chama o caso de uso.
3. Veja os DTOs em `adapters/in/rest/dto`: validações e exemplos Swagger ficam próximos dos dados de entrada e saída.
4. Siga o método do caso de uso em `application`: ele coordena o domínio e as portas sem depender de Spring.
5. Leia `domain` e `domain/port`: regras financeiras e contratos não conhecem JDBC, Kafka, Spring ou Swagger.
6. Veja a implementação da porta em `adapters/out`: persistência, mensageria e integração externa ficam nessa camada.
7. Consulte `bootstrap`: é onde Spring liga as implementações aos casos de uso.

## Exemplo: registrar um pagamento

`PaymentController.create` recebe CreatePaymentRequest, exige payments/create e resolve o tenant. `SubmitPayment.execute` valida a chave idempotente, cria Payment e calcula a impressão digital canônica. `JdbcPaymentStore.register` serializa comandos com a mesma chave e grava pagamento/outbox na mesma transação. `OutboxPublisher.publish` envia o evento para Kafka. `PaymentProjection.consume` deduplica e atualiza a consulta.

A resposta HTTP 202 significa registro aceito. Não significa autorização, captura ou liquidação. O Core desta versão permanece em CREATED.

## Convenções de legibilidade

- Java usa quatro espaços, UTF-8 e uma instrução por linha, seguindo Google Java Format no estilo AOSP.
- Condicionais e laços têm blocos explícitos. Evite ternários encadeados para decisões de negócio.
- Use nomes que indiquem responsabilidade: paymentStore, tenantContext, requestFingerprint, captureAmount.
- Valores financeiros são long em centavos. A moeda é um campo explícito.
- Separe DTOs de transporte dos modelos de domínio. Não introduza Swagger ou Spring no domínio.
- JavaDoc deve explicar finalidade, unidade, efeito colateral e limitações. Documente @param, @return e exceções relevantes quando ajudarem o consumidor.
- Registre em comentários o motivo de locks, idempotência ou decisões de segurança. Evite comentários que apenas repitam a instrução Java.
- As anotações Swagger descrevem dados, scopes, respostas e operações pendentes. Atualize-as junto da API.
- Execute `mvn spotless:apply`, revise o diff e depois `mvn verify` e `mvn spotless:check`.

## Pontos que exigem atenção

- Outbox/inbox não oferecem exactly-once financeiro. Consumidores ainda precisam de idempotência de negócio.
- Um resultado UNKNOWN não autoriza enviar nova cobrança a outra adquirente.
- PendingAdapter e UnconfiguredAcquirer falham explicitamente. Não trocar o retorno por sucesso apenas para concluir um teste.
- Os simuladores de autorização usam amount=13 para UNKNOWN e amount=51 para DECLINED, somente em local.
- Os métodos da entidade Payment representam resultados confirmados. Não os chamar para presumir que uma API externa teve sucesso.

## Compatibilidade desta refatoração

O pacote Java mudou de br.com.includelabs.gateway para br.com.includelabs.inpay. GatewayEvent passou a InPayEvent (os campos JSON foram preservados). O prefixo de configuração Spring mudou de gateway para inpay. URLs, tópicos, tabelas, chaves idempotentes e campos dos comandos foram preservados. As classes DTO foram movidas para o pacote dto; integrações Java que as importem precisam ajustar imports.
