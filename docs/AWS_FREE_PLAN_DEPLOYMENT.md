# AWS Free Plan — implantação demonstrativa do InPay

## Restrições aprovadas
- Manter a conta no plano gratuito; não ativar recursos avançados, upgrade ou serviços exclusivos do plano pago.
- Usar somente créditos disponíveis e serviços autorizados pela modalidade da conta. Não executar apply da fundação Terraform existente, que inclui MSK Serverless e RDS Multi-AZ.
- Usar dados sintéticos, sem documentos reais, credenciais de adquirentes, transações financeiras reais ou ativação produtiva.
- Preservar main; alterações passam por develop, staging e main mediante PR e validação.

## Ambientes
| Branch | Ambiente | Operação inicial |
|---|---|---|
| develop | dev | Testes e desenvolvimento sob demanda |
| staging | stg | Homologação simulada sob demanda |
| main | prd-demo | Demonstração comercial, sem produção financeira |

A primeira implantação será um único ambiente demonstrativo. Os demais ambientes não serão provisionados simultaneamente sem verificação de capacidade e créditos.

## Infraestrutura inicial
Região sugerida: us-east-2, condicionada à disponibilidade da conta.
Verificar no console o saldo, expiração e tipos EC2 elegíveis. Avaliar m7i-flex.large (8 GiB, x86_64) se disponível; não presumir instâncias de 16/32 GiB elegíveis. O Compose completo requer aproximadamente 16 GiB para execução local conforme README; em 8 GiB executar apenas os componentes necessários, com limites de memória e testes de saúde. Separar bancos/usuários/volumes por ambiente quando houver capacidade. Não expor portas PostgreSQL, Kafka, WireMock, Grafana, Swagger ou administração na internet.

Priorizar Docker Compose para primeira demonstração; K3s e Argo CD depois de aferir capacidade. Manter Kafka e Grafana autogerenciados no laboratório quando necessários. Cognito, S3, ECR e Lambda só após confirmar disponibilidade e consumo. Não provisionar EKS, MSK, NAT Gateway ou RDS Multi-AZ neste plano.

## Acesso e CI/CD
OIDC externo indisponível na modalidade simplificada atual. Não usar chaves root ou tokens persistentes compartilhados no chat, código ou logs. Usar sessão local temporária autorizada e avaliar mecanismo de deployment suportado sem upgrade. Os workflows verify existentes continuam sendo a barreira de validação; publicação automática permanece bloqueada até existir autorização segura, limites de consumo e validação da conta. Nenhum deploy AWS foi executado por esta alteração.

## Operação e encerramento
Monitorar saldo e data de expiração, parar EC2 quando não utilizada e observar custos residuais de EBS, snapshots e IPs. Exportar backups e artefatos antes do fim dos créditos ou prazo da conta. Argo CD gerencia workloads Kubernetes, não o ciclo de vida da própria EC2. Não habilitar recursos reais de pagamento em ambiente demonstrativo.

Referências oficiais:
- https://aws.amazon.com/free/
- https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/ec2-free-tier-usage.html
- https://docs.aws.amazon.com/accounts/latest/reference/supported-services-sign-up-new.html
