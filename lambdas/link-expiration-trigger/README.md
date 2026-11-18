# link-expiration-trigger

Handler: `br.com.includelabs.inpay.lambda.linkexpirationtrigger.Handler::handleRequest`. Java 21, pacote Maven shaded. Configurar TOKEN_ENDPOINT, CLIENT_ID, CLIENT_SECRET (injeção segura), TARGET_ENDPOINT e TARGET_SCOPE. Agendamento e DLQ devem ser provisionados conforme infra/terraform/README.md. Não efetua operação financeira diretamente.

No docker-compose.yml, INPAY_RUNTIME=local ativa Basic para chamar o backend local. Fora desse modo, a autenticação exige Cognito e HTTPS. A função é invocada manualmente pela porta do Runtime Interface Emulator; consulte o README raiz.
