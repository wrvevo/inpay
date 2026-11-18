# Gateway de pagamentos — estrutura Spring Boot

Estrutura de desenvolvimento baseada na arquitetura v1. Java 21 + Spring Boot 4.1.2, Maven multimódulo, PostgreSQL/Flyway, Kafka, Spring Security/Cognito, Lambdas Java e fundação Terraform AWS.

**Este pacote é um scaffold compilável com componentes funcionais, não um gateway pronto para cobrar.** O Core registra pagamentos em CREATED e publica PaymentCreated; não executa a saga financeira. Os fluxos ainda não implementados falham explicitamente (501), e mocks só são ativados por `local`. Não inserir dados de cartão reais.

## Organização

- `services/`: 19 deploys Spring Boot independentes; cada um tem domain, domain/port, application, adapters/in/rest, adapters/out/postgres, adapters/out/kafka e bootstrap.
- `libs/contracts`: envelope de eventos; não contém entidades compartilhadas de banco.
- `libs/platform-security`: validação Cognito, scopes, tenant, cliente M2M e perfil local autenticado.
- `libs/platform-messaging`: outbox transacional, publisher e inbox, com tratamento Kafka/DLT.
- `lambdas/`: webhook-forwarder, reconciliation-trigger e link-expiration-trigger.
- `infra/local`: bancos/usuários isolados, OpenTelemetry Collector, Prometheus/Grafana.
- `infra/terraform`: fundação AWS; `DEPLOYMENT.md` detalha recursos e passos restantes.
- `docs/openapi/`: contratos OpenAPI dos endpoints existentes, incluindo respostas 501 explícitas.
- `ARCHITECTURE.md`: modelo completo que orienta a evolução; contém funcionalidades planejadas além do scaffold.

## Compilar e testar

Requer **JDK 21 completo** e Maven 3.9+. O JRE sozinho não basta.

```bash
mvn -B verify
```

Testes padrão incluem regras financeiras, token por tenant/provedor, risco, roteamento, claims Cognito e ArchUnit. Integração real PostgreSQL fica no perfil explícito `mvn -B verify -Pintegration`, usando embedded-postgres (sem Docker). Esse teste baixa binário de teste, requer usuário não root e permissão para abrir sockets locais. Execução Kafka/AWS fica fora dessas suítes.

Para abrir no IntelliJ, VS Code ou Cursor, importar o `pom.xml` da raiz. Não criar um workspace separado por serviço.

## Rodar localmente

Requer Docker Compose. Senhas locais são deliberadamente de desenvolvimento; portas publicadas somente em loopback.

```bash
docker compose up -d postgres kafka
mvn -B install -DskipTests
```

Em terminais separados, executar os JARs desejados:

```bash
java -jar services/token-service/target/token-service-0.1.0-SNAPSHOT.jar --spring.profiles.active=local
java -jar services/payment-core-service/target/payment-core-service-0.1.0-SNAPSHOT.jar --spring.profiles.active=local
java -jar services/fraud-service/target/fraud-service-0.1.0-SNAPSHOT.jar --spring.profiles.active=local
java -jar services/transaction-query-service/target/transaction-query-service-0.1.0-SNAPSHOT.jar --spring.profiles.active=local
```

`./scripts/smoke.sh` cria token simulado, registra pagamento e avalia risco separadamente. A avaliação não dispara cobrança. Usuário HTTP Basic `developer`; senha padrão `local-only-change-me` (sobrescreva LOCAL_PASSWORD). Tenant fixo de desenvolvimento: `11111111-1111-1111-1111-111111111111`. Basic é restrito ao perfil local; não publicar essas portas.

Para rodar serviços em containers:

```bash
docker compose --profile apps up --build -d payment-core-service token-service fraud-service transaction-query-service
```

Todos os 19 simultaneamente podem consumir mais de 10 GB de RAM. Suba apenas os necessários. Para métricas com serviços em container: `docker compose --profile observability up -d prometheus grafana`. Ao alterar LOCAL_PASSWORD, atualizar a senha de scrape no Prometheus. Grafana em localhost:3000, login admin e senha local-only-change-me.

## Módulos e estágio real

| Serviço | Porta | Implementação |
|---|---:|---|
| merchant-service | 8081 | Domínio/port/use case e adapter pendente; onboarding e memberships não implementados. |
| payment-links-service | 8082 | Criação, consulta, revogação e job de expiração persistentes. Reserva/consumo ainda não conectados ao Core. |
| checkout-service | 8083 | Modelo e ports de sessão; ainda sem checkout público, UI, coleta de cartão e 3DS. |
| payment-core-service | 8084 | POST/GET persistentes, idempotência concorrente e outbox; máquina de estados testada. Saga/capture/refund/void HTTP retornam 501. |
| routing-service | 8085 | Política pura testada (bindings e UNKNOWN); cadastro e endpoint de seleção ainda pendentes. |
| token-service | 8086 | Alias aleatório e revogação apenas local. Não coleta PAN/CVV nem cria token de adquirente real. |
| fraud-service | 8087 | Motor nativo determinístico e assessment persistente. Sinais recebidos por API interna; extração/velocidade/revisão ainda pendentes. |
| webhook-ingress-service | 8088 | Ports e domínio; validação de callbacks e normalização ainda pendentes. |
| reconciliation-service | 8089 | Ports e domínio; job retorna 501 até implementar importação/consulta e comparação. |
| notification-service | 8090 | Domínio/port e assinatura HMAC; entrega/retry/SSRF/DLQ ainda pendentes. |
| transaction-query-service | 8091 | Projeção PaymentCreated via Kafka, inbox deduplicada e GET por tenant. Demais eventos ainda pendentes. |
| connector-cielo | 8101 | Simulador PostgreSQL local idempotente por operationId; adaptador real retorna 501. Não homologado. |
| connector-rede | 8102 | Simulador PostgreSQL local idempotente por operationId; adaptador real retorna 501. Não homologado. |
| connector-getnet | 8103 | Simulador PostgreSQL local idempotente por operationId; adaptador real retorna 501. Não homologado. |
| connector-stone | 8104 | Simulador PostgreSQL local idempotente por operationId; adaptador real retorna 501. Não homologado. |
| connector-bin | 8105 | Simulador PostgreSQL local idempotente por operationId; adaptador real retorna 501. Não homologado. |
| connector-global-payments | 8106 | Simulador PostgreSQL local idempotente por operationId; adaptador real retorna 501. Não homologado. |
| connector-pagbank | 8107 | Simulador PostgreSQL local idempotente por operationId; adaptador real retorna 501. Não homologado. |
| connector-safrapay | 8108 | Simulador PostgreSQL local idempotente por operationId; adaptador real retorna 501. Não homologado. |

## Segurança e produção

Default profile é `aws`, que exige configuração de banco, Kafka e Cognito. Não há fallback de AWS para Basic/mock. Cada endpoint de negócio usa scope explícito; o token deve ter token_use=access, expiração e client_id permitido, além de assinatura/issuer válidos. Aud só é exigido quando configurado para resource binding.

`COGNITO_CLIENT_TENANTS` faz binding M2M de lojistas. `COGNITO_TRUSTED_SERVICES` permite contexto de tenant para workloads internos; restringir aos chamadores esperados por destino e conferir ownership no use case. Login humano/memberships ainda não está implementado. Secrets nunca devem ser colocados no git.

Sem PAN/CVV no modelo ou eventos. Tokenização real demanda coletor contratado/verificado e bindings cifrados com KMS. Não substituir tokens simulados por números reais de cartão. Conectores reais precisam de contrato, mapeamento de códigos, consulta de UNKNOWN, regras de captura/cancelamento/estorno e testes de homologação.

Outbox/inbox implementam entrega ao menos uma vez, não exactly-once financeiro. O publisher mantém locks enquanto aguarda ACK de lote pequeno; para alto volume, implementar relay/CDC e medir contenção. PaymentCreated contém dados mínimos, não inclui token de instrumento. DLT não deve ser reproduzida sem verificar invariantes.

## Próxima implementação no código

1. Merchant/afiliação e resolução real de instrumentos; não permitir operação com alias não verificado.
2. Worker da saga: risco, rota persistida, autorização, consulta de UNKNOWN e captura com controle concorrente. Conectar os ports existentes.
3. Finalizar adaptador Cielo sandbox e coleção de testes de contrato; depois Rede e Getnet.
4. Checkout público, sessão, reservas de link, coleta segura e 3DS.
5. Webhooks verificados, compensações, conciliação e entrega segura de notificações.

Veja `VALIDATION.md` para o que foi efetivamente testado. Nenhum recurso foi implantado na AWS, nenhuma cobrança foi feita e nenhuma adquirente foi homologada.

## Referências

- Spring Boot: https://docs.spring.io/spring-boot/system-requirements.html
- JWT Resource Server: https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html
- Cognito M2M: https://docs.aws.amazon.com/cognito/latest/developerguide/cognito-user-pools-define-resource-servers.html
- Cielo: https://docs.cielo.com.br/ecommerce-cielo/docs/caracteristicas-api-ecommerce
