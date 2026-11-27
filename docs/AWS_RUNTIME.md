# Configuração AWS do InPay

| Ambiente | Segredo em `us-east-2` |
| --- | --- |
| DEV | `inpay/dev/runtime` |
| STG | `inpay/stg/runtime` |
| PRD demo | `inpay/prd-demo/runtime` |

Cada segredo contém um objeto JSON por módulo. Os valores são strings de configuração. O cadastro inicial mantém os conectores bancários e o agendador de billing desativados e define os escopos dos dois jobs internos. Atualize somente a seção do módulo desejado e preserve as demais seções na nova versão.

## Expiração de links e conciliação

Use o JAR sombreado produzido pelo Maven em cada módulo. Em AWS, instale a extensão AWS Parameters and Secrets para o runtime e arquitetura utilizados, conceda `secretsmanager:GetSecretValue` no ARN exato do segredo do ambiente e configure `INPAY_RUNTIME_SECRET_ID` com esse identificador. A extensão consulta a versão corrente durante a invocação, com cache controlado por `SECRETS_MANAGER_TTL`. Separe as funções e as permissões de cada ambiente.

Cada módulo de job lê `TARGET_ENDPOINT`, `TOKEN_ENDPOINT`, `CLIENT_ID`, `CLIENT_SECRET` e `TARGET_SCOPE` da seção correspondente. Os destinos devem usar HTTPS. A implantação existente por variáveis individuais continua disponível quando o identificador do segredo está ausente. O Compose de desenvolvimento continua usando sua própria autenticação local.

- `link-expiration-trigger` chama `/internal/v1/links/expire` com o escopo `links/write`. A publicação depende de um backend acessível e de um cliente M2M específico do ambiente.
- `reconciliation-trigger` chama `/internal/v1/reconciliation/run` com o escopo `reconciliation/run`. O backend retorna HTTP 501 até existir um adaptador de liquidação. Agende o job somente após validar o adaptador e o fluxo com dados sintéticos.
- `webhook-forwarder` continua recusando callbacks enquanto a verificação por provedor e a persistência durável estiverem pendentes.

Os painéis publicados no Amplify são estáticos. A instalação dos consumidores, o preenchimento de credenciais e destinos reais e qualquer agendamento são etapas da implantação do backend.
