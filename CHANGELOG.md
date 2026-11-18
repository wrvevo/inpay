# Alterações

## 0.2.0 — 18/11/2026

- Nome do produto definido como InPay; artefato raiz inpay e pacote br.com.includelabs.inpay.
- Prefixo Spring gateway substituído por inpay; GatewayEvent renomeado para InPayEvent, sem alterar os campos JSON.
- Java formatado em quatro espaços e blocos explícitos; variáveis dos fluxos principais receberam nomes descritivos.
- Regras Payment, SubmitPayment e RiskPolicy reorganizadas para evitar decisões encadeadas e expressões difíceis de acompanhar.
- JavaDoc nos métodos e construtores explícitos; comentários de idempotência, transação e limitações.
- DTOs separados dos controllers, com documentação de campos e exemplos Swagger.
- Springdoc 3.2.0 e biblioteca platform-openapi; Swagger local por serviço, desabilitado por padrão na AWS.
- Testes reais de documentação gerada e manutenção da autenticação das APIs.
- docker-compose.yml único, com 19 serviços, 3 Lambdas emuladas e infraestrutura de dados/observabilidade.
- Build Docker compartilhado, criação explícita de tópicos, healthchecks, volumes e agente OpenTelemetry.
- Guia de código, instruções Windows/Linux e diagnóstico local.

Bancos, tabelas, tópicos e comandos HTTP foram preservados. A refatoração não completa as integrações financeiras pendentes nem habilita cobranças reais.
