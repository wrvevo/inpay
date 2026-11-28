# InPay Cloud Native — segurança, autonomia e desempenho

## Decisão de arquitetura
Kafka é o barramento de eventos de domínio e comandos assíncronos duráveis, não um substituto universal para HTTP. Autorização de pagamento, roteamento e risco imediato precisam de orçamento de latência; usar REST/gRPC com autenticação de workload, deadlines e bulkheads. Eventos, notificações, conciliação e homologação usam processamento assíncrono com Outbox/Inbox, contratos AsyncAPI e schemas versionados. O identificador da entidade e do tenant deve compor a chave de partição; não usar PAN, CPF, tokens de acesso ou dados de autenticação como chave. A entrega é at-least-once, não exactly-once financeiro. ACK do broker não confirma pagamento. Reenvio após resultado incerto só com evidência do provedor e chave idempotente.

## Controles PCI DSS v4.0.1
- Escopo: CDE e sistemas conectados/que impactam sua segurança. Segregar conta/VPC, IAM, workloads, dados, CI/CD e logs. Validar segmentação com testes. Tokenização reduz escopo apenas quando comprovado.
- Dados: não armazenar CVV/CVC, trilha completa, PIN/bloco após autorização, nem criptografados. Preferir captura/tokenização hospedada pelo provedor; fora da fronteira controlada, eventos contêm somente identificadores opacos e dados mínimos. Nenhum payload nativo em Kafka, logs, traces, DLQ ou relatórios; validar schemas de allowlist e egress. O filtro heurístico desta revisão é defesa adicional e não comprova ausência de dados.
- Transporte: TLS forte, validação de hostname/certificado; mTLS quando exigido pelo parceiro; OAuth2 M2M com escopos mínimos, tokens de curta duração, rotação e identidade IAM. Kafka MSK SASL_SSL + IAM, políticas por tópico e consumer group; desabilitar auto-create e acesso público, criptografia, retenção e trilhas segregadas.
- Segredos em Secrets Manager/KMS e rotação; sem segredos em Terraform state não protegido, imagens, logs ou artefatos. MFA para pessoas, identidades individuais, separação de funções, acesso emergencial auditado.
- SDLC: revisão independente, SAST, SCA, SBOM, scanner de imagens, secrets scanning, proveniência e assinatura, aprovação de promoção por ambiente. Não publicar artefatos reempacotados sem validação.
- Auditoria: trilhas imutáveis, relógios sincronizados, acesso restrito, alertas, monitoramento e retenção segundo requisitos aplicáveis. Validar PCI DSS com QSA e adquirentes. Nenhuma certificação é alegada.

## Distribuição por equipes
Hoje o monorepo tem 34 serviços, 7 bibliotecas, 3 Lambdas e um reactor Maven. Permite build seletivo (mvn -pl services/payment-core-service -am verify), mas acesso de leitura ao repositório revela todo o código e CODEOWNERS não limita leitura. Há dependência de teste do payment-core em JARs plain de Itaú/Santander; substituir por testes de contrato contra stubs versionados.

Modelo-alvo: organização Include Labs com repositórios privados por bounded context, não um repositório por cada microsserviço indiscriminadamente:
| Repo proposto | Responsabilidade |
|---|---|
| inpay-platform | parent/BOM, starters e padrões CI, sem segredos |
| inpay-contracts | OpenAPI, AsyncAPI, schemas e mocks sintéticos |
| inpay-payment-core | gateway, orquestração, roteamento, tokenização controlada |
| inpay-bank-connectors | bancos e Pix; separar parceiros quando exigir acesso distinto |
| inpay-card-connectors | adquirentes; separar parceiros quando exigir acesso distinto |
| inpay-risk | risco e parceiros antifraude |
| inpay-accounts-ledger | contas e ledger, domínio financeiro restrito |
| inpay-marketplace-credits | marketplace e benefícios |
| inpay-billing-reconciliation | cobrança, conciliação e relatórios |
| inpay-onboarding | homologação e documentos |
| inpay-portals | aplicações e checkout |
| inpay-infra-gitops | IaC, manifests, digests e políticas de ambiente |

Cada equipe acessa apenas repositórios de seu domínio e contratos/artefatos compartilhados. Registry Maven (GitHub Packages ou CodeArtifact conforme elegibilidade) para parent/BOM e bibliotecas com SemVer e releases imutáveis; ECR/registry OCI por serviço. Nenhum serviço depende de código-fonte de outro. Consumir versões fixas, evitar SNAPSHOT em produção; promover mesmo digest DEV -> STG -> PRD. Acesso ao registro é distinto de acesso ao fonte, verificar a granularidade do produto escolhido (Maven GitHub Packages é vinculado ao repo). Criar equipes, regras e repositórios privados exige permissões administrativas e não foi feito nesta revisão. O workflow build-component cria JAR e checksum por componente, sem publicar a registro ou dar isolamento de leitura.

## Pendências impeditivas de produção
1. Journal durável por tenant, afiliação, operação, ambiente e chave; hash canônico, reserva atômica, estados UNKNOWN e reconciliação. Memória local não é solução distribuída. A API bancária genérica e os endpoints especializados C6/Itaú/Santander foram restringidos ao perfil de laboratório enquanto faltam esses controles e credenciais M2M. Não confundir com ativação produtiva.
2. Unificar orquestração e roteamento com configurações de Accounts; preservar transição atômica e outbox, evitar fallback cego. Validar ledger de partidas dobradas e reconciliação por provedor.
3. Webhooks: assinatura verificada, replay protection, inbox durável, consulta de estado ao provedor; sem adaptadores pendentes na produção. Tokenização homologada, sem fixtures em produção.
4. Outbox: reserva por lease e ACK fora da transação, migrations aditivas; duplicatas continuam possíveis após falha/expiração. Consumidores precisam deduplicar e aplicar versão monotônica. Migrar com rollout coordenado: não executar publicadores antigos e novos simultaneamente, pois versões antigas ignoram lease. Monitorar atraso, tentativas, DLQ e leases expirados. Não confundir limite de espera com garantia de tempo real (send pode bloquear antes de devolver future). Medir antes de otimizar. Avaliar Debezium CDC quando a capacidade justificar.
5. Configurar autenticação M2M e propagação de tenant com autorização por afiliação, validar perfis mutuamente exclusivos e bloquear Basic e mocks fora de laboratório.
6. Terraform por ambiente, ECS/EKS conforme custo e benchmark, RDS Multi-AZ, MSK, HPA/KEDA, quotas, backups e restauração. Plano gratuito atual: não provisionar recursos pagos. Não misturar dados reais e demo.
7. Testes k6/Gatling, duplicação concorrente, indisponibilidade de broker/provedor, failover, teste de recuperação e isolamento. Metas p95/p99 e TPS somente após benchmark; revisão de risco com evidências.

Referências: https://www.pcisecuritystandards.org/standards/pci-dss/ ; https://docs.aws.amazon.com/msk/latest/developerguide/bestpractices-kafka-client.html ; https://docs.github.com/en/packages/learn-github-packages/about-permissions-for-github-packages
