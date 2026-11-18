# link-expiration-trigger

Handler: `br.com.includelabs.gateway.lambda.linkexpirationtrigger.Handler::handleRequest`. Java 21, pacote Maven shaded. Configurar TOKEN_ENDPOINT, CLIENT_ID, CLIENT_SECRET (injeção segura), TARGET_ENDPOINT e TARGET_SCOPE. Agendamento e DLQ devem ser provisionados conforme infra/terraform/README.md. Não efetua operação financeira diretamente.
