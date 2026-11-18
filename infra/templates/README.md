# Templates de implantação

`ecs-task-definition.json` é um template explícito: substituir todos os REPLACE, porta, scopes/identidades e segredo por módulo. Criar serviço ECS, listener/TLS, healthcheck e regras de rede separadamente. Não registrar sem revisão. Role de execução acessa somente os secrets injetados e ECR/logs; task role recebe permissões MSK/KMS próprias. Nenhuma imagem nem infraestrutura foi publicada.
