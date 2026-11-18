# Implantação AWS — contrato por workload

1. Construir cada imagem com Dockerfile/ARG MODULE e publicar no ECR da conta.
2. ECS/Fargate: uma task definition/service por módulo, rede privada, logs por serviço, healthcheck, deployment circuit breaker e role independente. Permitir entrada apenas do ALB interno/serviços autorizados; APIs externas passam por API Gateway/ALB com WAF e TLS.
3. Injetar DB_URL, DB_USER e DB_PASSWORD (Secrets Manager). Nunca usar superusuário da base. Uma base e usuário por módulo; migrations executadas pelo owner dedicado.
4. Cognito: COGNITO_ISSUER, COGNITO_ALLOWED_CLIENTS (CSV), COGNITO_CLIENT_TENANTS (`client=uuid,client=uuid`) e COGNITO_TRUSTED_SERVICES (CSV). COGNITO_AUDIENCE só quando usar resource binding. Não confundir secret do cliente chamador com chaves de validação do servidor.
5. MSK: KAFKA_BOOTSTRAP_SERVERS IAM/TLS. Task role recebe kafka-cluster:Connect no cluster, WriteData/DescribeTopic apenas nos tópicos emitidos, ReadData/DescribeTopic nos tópicos consumidos e AlterGroup/DescribeGroup no seu consumer group. Criar tópicos versionados e .DLT antes de iniciar consumidores; configurar partições/retention por volume real.
6. Secrets Manager/GetSecretValue e KMS/Decrypt apenas para ARNs necessários ao workload. Para cartões, implementar envelope encryption com encryption context de tenant/binding no Token Service antes de habilitar bindings reais.
7. Lambdas: empacotar os JARs de lambdas/*; Java 21; timeout 30 s; concurrency limitada; VPC quando chamar backend privado; secrets injetados com extensão/SDK (o scaffold recebe ambiente); role de execução mínima. EventBridge Scheduler invoca triggers com role dedicada, retry e DLQ SQS. Não habilitar webhook-forwarder enquanto retornar 501.
8. Observabilidade: passar OpenTelemetry Java Agent por `JAVA_TOOL_OPTIONS=-javaagent:/otel/opentelemetry-javaagent.jar`, OTEL_SERVICE_NAME e OTEL_EXPORTER_OTLP_ENDPOINT. A imagem Docker local inclui agente 2.22.0. Para produção, revisar versão e política de supply chain antes da publicação. Não capturar headers Authorization, tokens ou corpos de pagamento. Collector local já aceita OTLP; backend de traces AWS deve ser configurado.
9. RDS Proxy apenas para workloads Lambda que acessarem PostgreSQL diretamente; triggers atuais usam HTTP e não precisam de conexão SQL.
10. Checkout público exige UI, coleta de cartão homologada, sessão/CSRF/CSP e WAF. Não existe UI de checkout nesta estrutura. Não publicar APIs /local na internet.

Sem deploy realizado. Conectores reais, saga financeira, tokenização real, webhooks externos, conciliação e entrega de notificações ainda devem ser implementados/homologados. As classes PendingAdapter e UnconfiguredAcquirer falham com 501 para não anunciar resultado financeiro fictício.


O Compose é exclusivo de desenvolvimento. INPAY_RUNTIME=local nas Lambdas e profile local nos serviços não devem ser usados em produção. Documentação Swagger permanece desabilitada por padrão no perfil AWS.
