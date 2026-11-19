# Validação do painel gerencial — 19/11/2026

- Frontend React/TypeScript: build de produção com Vite e verificação de tipos.
- 7 testes Node de domínio/transporte: valores monetários, CSV, split, antifraude, rotas, credenciais e proxy HTTP.
- 6 cenários Playwright: dashboard, filtros, detalhes, downloads, links, oito formulários de provedores, isolamento demo/conectado, contrato de links, erros de autorização, navegação desktop/móvel e simuladores.
- Revisão visual em desktop (1440px) e celular (390px).
- Compose validado estruturalmente, agora com 30 serviços/containers definidos incluindo o painel.
- `npm audit --omit=dev`: nenhuma vulnerabilidade reportada nas dependências de produção na execução desta revisão.
- Job frontend adicionado ao workflow de verificação com instalação reproduzível via lockfile.

Limites: o Docker Engine não está disponível e os serviços Java não estão rodando. O script `check-services.py` retornou conexão recusada, portanto não há validação ponta a ponta com serviços reais nesta entrega. Os testes de integração do painel usam upstreams controlados. Credenciais Cognito/adquirentes não foram usadas; homologação financeira e login interativo permanecem pendentes. Nenhum código Java foi alterado nesta mudança.

---

# Validação dos connectors — 19/11/2026

- Reactor Maven completo: **BUILD SUCCESS**, JDK 21.0.13, Maven 3.9.11, Spring Boot 4.1.2.
- **89 testes aprovados, 0 falhas, 0 erros, 0 ignorados**: 42 existentes e 47 novos.
- Os 32 testes dos adaptadores verificam contratos HTTP com servidor local, campos opcionais, rotas financeiras, validação e respostas incompletas.
- Os 15 testes do runtime verificam autenticação, cache/expiração, isolamento de tenant, idempotência com journal substituto, resultados incertos, proteção de dados e ausência de retry financeiro automático.
- `spotless:apply verify` executado no reactor completo (28 projetos, incluindo a nova biblioteca connector-runtime).
- `docker-compose config --quiet` aprovado; `git diff --check` sem erros.

## Limites desta entrega

- Testes usam contratos sintéticos e HTTP local; não substituem homologação com as adquirentes.
- Não há credenciais de homologação nem Docker Engine disponíveis. Containers, PostgreSQL real, mTLS com certificado do estabelecimento e Cognito real não foram exercitados nesta revisão.
- O journal JDBC exige PostgreSQL e foi revisado, mas seus cenários de concorrência/crash ainda precisam de validação integrada.
- Bin usa Carat/e-SiTef com mTLS. Global Payments usa GP-API e exige confirmação de habilitação para a afiliação brasileira.
- O escopo é o núcleo de cartões: autorização, captura, cancelamento/estorno e consulta. Outros produtos e a saga ponta a ponta permanecem conforme [CONNECTORS.md](docs/CONNECTORS.md).

---

# Validação do InPay 0.2.0 — 18/11/2026

## Executado nesta revisão

- Reactor Maven completo: **BUILD SUCCESS**, com JDK 21.0.13, Maven 3.9.11 e Spring Boot 4.1.2.
- 27 projetos Maven: raiz, 4 bibliotecas, 19 serviços Spring Boot e 3 Lambdas Java.
- **42 testes aprovados, 0 falhas, 0 erros, 0 ignorados**.
- A suíte preserva os 39 testes anteriores e adiciona 3 testes MockMvc para contrato OpenAPI, recursos Swagger UI e autenticação das APIs de negócio.
- O teste de Swagger inicializa o contexto Spring e consulta a documentação gerada; não usa um arquivo JSON previamente preparado.
- Springdoc 3.2.0 integrado e compatível com a linha Spring Boot 4.1.x conforme documentação oficial.
- Auditoria AST: **248 métodos/construtores explícitos de produção possuem JavaDoc**, sem ausências. Construtores compactos e accessors implícitos de records são contados separadamente pelo modelo Java e não fazem parte desse total.
- **42 operações HTTP anotadas** com documentação Swagger. DTOs de entrada e resposta recebem descrições e exemplos.
- `mvn spotless:apply spotless:check`: **BUILD SUCCESS**, estilo Java AOSP de quatro espaços.
- `docker-compose v2.39.4 config --quiet`: aprovado para os 29 containers definidos.
- Verificação estrutural de POM/XML, YAML e inventário de serviços.

## Limites da validação

- Não há Docker Engine disponível neste ambiente: as imagens não foram construídas nem os containers iniciados aqui. A validação do Compose verifica configuração, não execução ponta a ponta.
- Testes PostgreSQL do perfil integration continuam preparados, mas não executados nesta revisão. Kafka, Lambdas emuladas e observabilidade devem ser verificados no ambiente Docker do desenvolvedor.
- AWS, Cognito real e adquirentes não foram acessados ou homologados. Terraform não foi aplicado nem revalidado nesta revisão.
- Operações financeiras completas continuam pendentes. Endpoints marcados como 501 não foram convertidos em respostas de sucesso.

## Reproduzir

```bash
mvn -B spotless:check verify
mvn -B verify -Pintegration
docker compose config --quiet
docker compose up --build -d
python3 scripts/check-services.py
```

Os testes padrão não dependem da AWS. O perfil integration requer processos PostgreSQL locais e sockets permitidos. Consulte README para credenciais de desenvolvimento, URLs e limitações de cada módulo.
