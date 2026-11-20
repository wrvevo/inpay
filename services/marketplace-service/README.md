# Marketplace InPay

Serviço separado do gateway: contas, limites, repasses conciliados, vouchers, pedidos com duas fontes,
aprovação independente, journal e conciliação. Java 21, Spring Boot, PostgreSQL, Kafka e Cognito.

Documentação completa: [docs/MARKETPLACE.md](../../docs/MARKETPLACE.md).

- Porta: 8110.
- Base REST: `/marketplace/v1`.
- Swagger local: `/swagger-ui/index.html`.
- `application.yml`: infraestrutura; `marketplace.yml`: tenants explicitamente habilitados.
- `application-local.yml`: PostgreSQL/Kafka locais e identidades fictícias.
- `application-aws.yml`: configuração MSK IAM; credenciais Cognito via ambiente.
- `db/migration`: estado, journal imutável e outbox exclusivos.

O conector real Nuuvem/PlacarPlay não é presumido. Somente o perfil local simula fulfillment.
