# Validação — 16/11/2026

## Executado

- Reactor Maven completo: **BUILD SUCCESS** em Java 21.0.13 e Maven 3.9.11, Spring Boot 4.1.2.
- 26 projetos Maven no reactor: raiz, 3 bibliotecas, 19 serviços e 3 Lambdas.
- **39 testes aprovados, 0 falhas, 0 erros, 0 ignorados**: 19 verificações ArchUnit, 5 testes de claims Cognito, 6 de regras financeiras, 4 antifraude, 3 de token/tenant e 2 de roteamento.
- Compilação das fontes de integração PostgreSQL; execução reservada ao perfil integration.
- Parse de XML/POM, YAML/Compose e 19 contratos OpenAPI.
- `terraform fmt -check -recursive`: aprovado; inicialização do provider realizada sem backend e sem apply.

## Não executado / limites

- `terraform validate` não concluiu: provider AWS tentou abrir socket Unix e recebeu `operation not permitted` no ambiente. A formatação passou, mas isso NÃO comprova validação completa de schema ou implantação.
- PostgreSQL integrado: 4 cenários preparados em PostgresIdempotencyIT (concorrência, conflito de payload, isolamento de tenant e rollback/outbox). Não executados aqui; usar `mvn verify -Pintegration` em ambiente com sockets locais liberados.
- Não havia Docker para execução de Compose e testes de ponta a ponta com Kafka/PostgreSQL.
- Cognito real, Lambda/AWS, checkout público e endpoints de adquirentes não foram executados ou homologados.
- Serviços possuem estruturas e estágios diferentes, descritos no README. O Core registra CREATED; não processa cobranças. Conectores são simulações locais e falham com 501 fora desse perfil.

## Reproduzir

```bash
mvn -B verify
mvn -B verify -Pintegration
docker compose up -d postgres kafka
```

Os testes padrão não dependem da AWS. Revise README e DEPLOYMENT antes de provisionar recursos ou adicionar credenciais reais.
