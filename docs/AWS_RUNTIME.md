# Configuração AWS do InPay

| Ambiente | Segredo em `us-east-2` |
| --- | --- |
| DEV | `inpay/dev/runtime` |
| STG | `inpay/stg/runtime` |
| PRD demo | `inpay/prd-demo/runtime` |

Cada segredo é um objeto JSON plano. A chave usa o formato `módulo.VARIÁVEL`, por exemplo, `reconciliation-service.DB_URL` e `reconciliation-trigger.TARGET_SCOPE`. Um valor `null` indica configuração pendente. Valores operacionais são strings. Preserve as outras chaves ao atualizar a versão corrente.

Para implantar um serviço Spring, injete a chave qualificada do segredo na variável simples que o `application.yml` espera. Por exemplo, injete `reconciliation-service.DB_URL` como `DB_URL`. Faça o mesmo para `DB_USER`, `DB_PASSWORD`, `KAFKA_BOOTSTRAP_SERVERS` e as chaves `COGNITO_*` do serviço. Provisione os recursos reais antes de preencher esses valores. Em Ohio, os bancos RDS e os pools de usuários Cognito ainda não existem.

## Expiração de links e conciliação

Use o JAR sombreado produzido pelo Maven em cada módulo. Em AWS, instale a extensão AWS Parameters and Secrets para o runtime e arquitetura utilizados, conceda `secretsmanager:GetSecretValue` no ARN exato do segredo do ambiente e configure `INPAY_RUNTIME_SECRET_ID` com esse identificador. A extensão consulta a versão corrente durante a invocação, com cache controlado por `SECRETS_MANAGER_TTL`. Separe as funções e as permissões de cada ambiente.

Cada módulo de job lê suas chaves qualificadas para `TARGET_ENDPOINT`, `TOKEN_ENDPOINT`, `CLIENT_ID`, `CLIENT_SECRET` e `TARGET_SCOPE`. Os destinos devem usar HTTPS. A implantação existente por variáveis individuais continua disponível quando o identificador do segredo está ausente. O Compose de desenvolvimento continua usando sua própria autenticação local.

- `link-expiration-trigger` chama `/internal/v1/links/expire` com o escopo `links/write`. A publicação depende de um backend acessível e de um cliente M2M específico do ambiente.
- `reconciliation-trigger` chama `/internal/v1/reconciliation/run` com o escopo `reconciliation/run`. Vincule o cliente Cognito ao lojista autorizado em `COGNITO_CLIENT_TENANTS`. O serviço compara registros importados e salva o resultado. A publicação requer backend HTTPS, banco, Cognito e dados de teste. Consulte o [guia do serviço](../services/reconciliation-service/README.md).
- `webhook-forwarder` continua recusando callbacks enquanto a verificação por provedor e a persistência durável estiverem pendentes.

Os painéis publicados no Amplify são estáticos. A instalação dos consumidores, o preenchimento de credenciais e destinos reais e qualquer agendamento são etapas da implantação do backend.
