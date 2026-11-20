# InPay — gateway de pagamentos modular

Java 21 · Spring Boot 4.1.2 · PostgreSQL · Kafka · Cognito · arquitetura hexagonal

O projeto foi refatorado para facilitar leitura e manutenção por desenvolvedores plenos: indentação de quatro espaços em Java, blocos explícitos, nomes descritivos nos fluxos principais, DTOs separados, JavaDoc e Swagger gerado pelos controllers.

**Estágio real:** estrutura de desenvolvimento com componentes funcionais. O Core registra pagamentos em CREATED e publica PaymentCreated, mas ainda não executa a saga financeira completa. Os connectors agora incluem chamadas HTTP externas de autorização, captura, cancelamento/estorno e consulta, desabilitadas por padrão e ainda dependentes de homologação com suas credenciais. O perfil local preserva os simuladores. Consulte [CONNECTORS.md](docs/CONNECTORS.md). O ciclo de crédito Cielo foi exercitado em sandbox pelas APIs internas do InPay; cobertura e limitações estão em [CIELO_COLLECTION_COVERAGE.md](docs/CIELO_COLLECTION_COVERAGE.md).

## Marketplace independente

O serviço opcional `marketplace-service` (8110) adiciona contas de crédito, saldos de repasses,
vouchers, pagamento combinado com até duas fontes, aprovação independente, journal e conciliação.
Possui banco, APIs, escopos e tópico próprios. O gateway continua atendendo os demais clientes.
Os painéis **Créditos e saldos** e **Meu crédito e compras** consomem as novas APIs.
Fulfillment é simulado somente em local; Nuuvem/gateway reais permanecem bloqueados até homologação.
Consulte [arquitetura, execução, controles e limites](docs/MARKETPLACE.md).

## Painel gerencial

O frontend em `frontend/` oferece visão geral, transações, links, financeiro, antifraude, clientes, recebedores, assinaturas, split, contestações e configuração assistida dos oito connectors.

Após `docker compose up --build -d`, acesse **http://localhost:3080**. A interface inicia em demonstração, com dados fictícios identificados. O modo conectado permite consultar transações, criar/consultar/revogar registros de links e avaliar risco. Demais áreas são demonstrações ou templates até existirem APIs administrativas. Consulte [frontend/README.md](frontend/README.md) para execução, segurança e limites.

## Subir o ambiente completo com Docker

Requer Docker Engine/Desktop com Compose v2 e acesso aos registries Maven, Docker Hub e ECR público. Para todos os módulos, reservar aproximadamente 16 GB de RAM e pelo menos 4 CPUs para Docker. O consumo varia durante build e inicialização.

```bash
# Opcional: copie .env.example para .env e ajuste as senhas locais.
docker compose up --build -d

docker compose ps
docker compose logs -f payment-core-service
```

No Windows/PowerShell, a cópia opcional é `Copy-Item .env.example .env`; no Linux/macOS, `cp .env.example .env`. O Compose já fornece valores de desenvolvimento quando .env não existe.

O `docker-compose.yml` sobe, sem precisar ativar profiles:

- 19 serviços Spring Boot, cada um com banco/usuário próprio no PostgreSQL;
- 3 containers de emulação das Lambdas Java;
- PostgreSQL, Kafka e criação explícita dos tópicos/DLT;
- OpenTelemetry Collector, Prometheus e Grafana;
- um job auxiliar que prepara a credencial local de coleta de métricas.

São 30 containers definidos, incluindo 2 jobs de inicialização que terminam com sucesso. As portas publicadas ficam somente em `127.0.0.1`. A compilação usa uma etapa Docker compartilhada entre os módulos. O primeiro build baixa dependências e pode demorar; o Docker reutiliza cache nas próximas execuções.

**Subir todos os processos não completa as funcionalidades pendentes.** Checkout ainda não possui interface pública; callbacks reais e conciliação ainda não foram implementados. Sem habilitação explícita, nenhum container local realiza cobrança real. Os novos endpoints externos podem ser habilitados por configuração para testes em sandbox.

## Swagger e autenticação

Abra o Swagger do Payment Core: **http://localhost:8084/swagger-ui/index.html**.

Cada serviço disponibiliza documentação em sua própria porta:

- `/swagger-ui/index.html`: interface navegável;
- `/v3/api-docs`: contrato OpenAPI JSON;
- `/v3/api-docs.yaml`: contrato OpenAPI YAML.

No perfil `local`, a documentação é pública na máquina local. Para executar operações pelo botão **Authorize**, use HTTP Basic: usuário `developer`, senha `local-only-change-me` ou o valor configurado em LOCAL_PASSWORD. Acesso à documentação não libera as APIs de negócio. O tenant local é `11111111-1111-1111-1111-111111111111`.

No perfil AWS, documentação e Swagger ficam desabilitados por padrão. Se forem habilitados explicitamente, as rotas de documentação exigem o escopo `operations/read`, além da proteção de rede. Não inserir client secret Cognito no navegador: as APIs recebem access tokens obtidos no backend.

Grafana: **http://localhost:3000**, usuário `admin`, senha local definida por GRAFANA_PASSWORD. Prometheus: **http://localhost:9090**. A senha usada no scrape é sincronizada pelo job observability-init. O Collector recebe traces em 4317/4318 e os exporta para logs; este Compose não inclui armazenamento ou interface de busca de traces.

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
| connector-cielo | 8101 | 20 operações REST da coleção oficial, journal PostgreSQL e Swagger. Ciclo de crédito validado em sandbox; recorrência exercitada; tokenização pendente no estabelecimento. |
| connector-rede | 8102 | Simulador PostgreSQL local idempotente por operationId; adaptador HTTP externo configurável, com journal de idempotência. Não homologado. |
| connector-getnet | 8103 | Simulador PostgreSQL local idempotente por operationId; adaptador HTTP externo configurável, com journal de idempotência. Não homologado. |
| connector-stone | 8104 | Simulador PostgreSQL local idempotente por operationId; adaptador HTTP externo configurável, com journal de idempotência. Não homologado. |
| connector-bin | 8105 | Simulador PostgreSQL local idempotente por operationId; adaptador HTTP externo configurável, com journal de idempotência. Não homologado. |
| connector-global-payments | 8106 | Simulador PostgreSQL local idempotente por operationId; adaptador HTTP externo configurável, com journal de idempotência. Não homologado. |
| connector-pagbank | 8107 | Simulador PostgreSQL local idempotente por operationId; adaptador HTTP externo configurável, com journal de idempotência. Não homologado. |
| connector-safrapay | 8108 | Simulador PostgreSQL local idempotente por operationId; adaptador HTTP externo configurável, com journal de idempotência. Não homologado. |


## Lambdas locais

As Lambdas usam o Runtime Interface Emulator da imagem oficial AWS. Elas são invocadas manualmente; o Compose não cria agendamentos EventBridge.

| Lambda | Porta | Comportamento |
|---|---:|---|
| webhook-forwarder | 9001 | Retorna 501 até implementar verificação e persistência do callback. |
| reconciliation-trigger | 9002 | Chama o serviço de conciliação; enquanto o backend retornar 501, a invocação informa falha. |
| link-expiration-trigger | 9003 | Chama o endpoint que revoga links locais vencidos. |

Exemplo, após os serviços ficarem saudáveis:

```bash
curl -X POST http://localhost:9003/2015-03-31/functions/function/invocations -d '{}'
```

No PowerShell, use `curl.exe` para executar esse exemplo. O modo local é ativado explicitamente por INPAY_RUNTIME=local e usa Basic apenas entre containers. Fora dele, as Lambdas exigem HTTPS e Cognito M2M. Nenhum fallback para credencial local ocorre após falha do Cognito.

## Compilar e testar sem Docker

Requer JDK 21 completo e Maven 3.9+:

```bash
mvn -B verify
mvn -B spotless:check
# Após alterar Java, aplicar a mesma formatação:
mvn -B spotless:apply
```

Para testes PostgreSQL com processos locais liberados: `mvn -B verify -Pintegration`. Docker não é necessário para esse perfil, mas o teste requer usuário não root e permissão para abrir sockets locais.

Importe o `pom.xml` raiz no IntelliJ, VS Code ou Cursor. Pacote base: `br.com.includelabs.inpay`. Artefato pai: `inpay`, versão `0.2.0-SNAPSHOT`.

## Navegação no código

- `services/`: 19 módulos independentes, organizados em domain, application, adapters e bootstrap.
- `libs/connector-runtime`: transporte HTTP, autenticação de adquirentes e journal de operações externas.
- `libs/contracts`: envelope de eventos, sem entidades compartilhadas entre bancos.
- `libs/platform-security`: Cognito, escopos, tenant e cliente M2M.
- `libs/platform-messaging`: outbox, inbox e tratamento Kafka/DLT.
- `libs/platform-openapi`: metadados InPay e segurança do Swagger.
- `lambdas/`: funções Java e modos de execução local/AWS.
- `docs/CODE_GUIDE.md`: roteiro de leitura das regras e convenções.
- `docs/LOCAL_ENVIRONMENT.md`: operação do Compose, persistência e diagnóstico.
- `docs/openapi/`: exemplos estáticos de contratos; o contrato gerado em `/v3/api-docs` é a referência do código atual.
- `DEPLOYMENT.md` e `infra/terraform/`: fundação AWS e passos ainda necessários.
- `VALIDATION.md`: verificações realmente executadas nesta versão.

## Parar sem apagar dados

```bash
docker compose down
```

Os volumes preservam PostgreSQL, Kafka, Prometheus e Grafana. `docker compose down -v` apaga esses dados locais; use somente para reiniciar deliberadamente o ambiente.

## Segurança e continuidade

Tokens simulados não substituem tokenização real. O caminho padrão usa tokens; payloads nativos com dados de cartão exigem habilitação explícita em ambiente PCI e não são persistidos. Bindings reais, checkout/3DS, saga, tratamento de UNKNOWN, callbacks, conciliação e entrega de notificações continuam como próximas implementações. Os detalhes de cada módulo estão na tabela acima.

Default profile das aplicações é `aws`; somente o Compose local ativa `local`. Cada API mantém seu scope e ownership. Alterar o nome Java não renomeou tabelas, bancos nem tópicos para evitar perda de compatibilidade de dados existentes.

Referências oficiais: [Springdoc](https://springdoc.org/), [Cognito M2M](https://docs.aws.amazon.com/cognito/latest/developerguide/cognito-user-pools-define-resource-servers.html), [Lambda Java em containers](https://docs.aws.amazon.com/lambda/latest/dg/java-image.html).
