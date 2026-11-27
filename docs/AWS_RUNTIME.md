# Configuração AWS do InPay

| Ambiente | Segredo em `us-east-2` |
| --- | --- |
| DEV | `inpay/dev/runtime` |
| STG | `inpay/stg/runtime` |
| PRD demo | `inpay/prd-demo/runtime` |

Esses três segredos são reservados ao `payment-core-service`, com chaves no formato `payment-core-service.VARIÁVEL`.

Cada módulo tem três segredos dedicados, nomeados `<módulo>-dev`, `<módulo>-stg` e `<módulo>-prd`. Eles contêm um objeto JSON plano com chaves simples como `DB_URL`. Uma string vazia indica configuração pendente e é compatível com a aba **Chave/valor** do console. Preserve as outras chaves ao atualizar a versão corrente.

## Cognito e publicação

Cada ambiente tem um pool e um cliente público Cognito próprios. Os perfis de painel são `panel-admin`, `panel-manager` e `panel-viewer`. Os perfis de homologação são `homologation-admin`, `homologation-pdv` e `homologation-backoffice`. O cadastro é iniciado por um administrador. A associação de usuários e o vínculo do login do painel ao Cognito exigem a configuração da aplicação.

O workflow **Publish AWS demo** executa em pushes nas branches de ambiente, incluindo os feitos por merge. A associação é `develop → dev`, `staging → stg` e `main → prd-demo`. Cada branch usa sua própria role via OIDC, limitada ao destino correspondente no Amplify. Depois da publicação, o workflow consulta `deployment.json` na URL do ambiente e exige que o commit publicado coincida com o commit do GitHub.

Para implantar um serviço Spring, injete as chaves do segredo dedicado nas variáveis que o `application.yml` espera. O banco deve usar um usuário próprio do serviço e conexão TLS com validação da CA do RDS. Informe `KAFKA_BOOTSTRAP_SERVERS` somente quando houver um cluster acessível. Configure a lista de clientes e os vínculos com lojistas somente para identidades autorizadas pelo módulo.

## Expiração de links e conciliação

Use o JAR sombreado produzido pelo Maven em cada módulo. Em AWS, instale a extensão AWS Parameters and Secrets para o runtime e arquitetura utilizados, conceda `secretsmanager:GetSecretValue` no ARN exato do segredo do ambiente e configure `INPAY_RUNTIME_SECRET_ID` com esse identificador. A extensão consulta a versão corrente durante a invocação, com cache controlado por `SECRETS_MANAGER_TTL`. Separe as funções e as permissões de cada ambiente.

Cada módulo de job deve apontar `INPAY_RUNTIME_SECRET_ID` para seu segredo dedicado. Ele lê `TARGET_ENDPOINT`, `TOKEN_ENDPOINT`, `CLIENT_ID`, `CLIENT_SECRET` e `TARGET_SCOPE`. Os destinos devem usar HTTPS. A implantação por variáveis individuais continua disponível quando o identificador do segredo está ausente. O Compose de desenvolvimento usa sua própria autenticação local.

- `link-expiration-trigger` chama `/internal/v1/links/expire` com o escopo `links/write`. A publicação depende de um backend acessível e de um cliente M2M específico do ambiente.
- `reconciliation-trigger` chama `/internal/v1/reconciliation/run` com o escopo `reconciliation/run`. Vincule o cliente Cognito ao lojista autorizado em `COGNITO_CLIENT_TENANTS`. O serviço compara registros importados e salva o resultado. A publicação requer backend HTTPS, banco, Cognito e dados de teste. Consulte o [guia do serviço](../services/reconciliation-service/README.md).
- `webhook-forwarder` continua recusando callbacks enquanto a verificação por provedor e a persistência durável estiverem pendentes.

Os painéis publicados no Amplify são estáticos. A instalação dos consumidores, o preenchimento de credenciais e destinos reais e qualquer agendamento são etapas da implantação do backend.
