# Infraestrutura recomendada e critérios de homologação

Recomendação técnica: AWS, aproveitando Cognito e Lambda já escolhidos. ECS/Fargate para os
serviços Spring e conectores; MSK para Kafka; RDS PostgreSQL Multi-AZ; Secrets Manager e KMS;
ALB/API Gateway e WAF na borda; sub-redes privadas para serviços, broker e bancos. Usar NAT/egress
controlado com IPs fixos quando o parceiro exigir allowlist. Lambda para adaptadores de eventos
curtos, notificações e tarefas específicas, sem assumir que uma transação distribuída é atômica.
Validar disponibilidade regional e custos dos serviços antes de contratar.

| Fornecedor | Composição possível | Critério de escolha |
|---|---|---|
| AWS | ECS/Fargate, MSK, RDS, Cognito, KMS/Secrets Manager, Lambda | Maior continuidade com esta arquitetura |
| Azure | AKS/Container Apps, PostgreSQL Flexible Server, Kafka gerenciado contratado, Key Vault, identidade | Bom se equipe e contratos já forem Microsoft; verificar equivalência Kafka |
| Google Cloud | GKE/Cloud Run, Cloud SQL, serviço Kafka, Secret Manager e IAM | Alternativa para equipe GCP; exige rever integração de identidade |

Serviços de nuvem no escopo PCI não tornam o InPay automaticamente conforme. Delimitar CDE,
fluxo de cartão e responsabilidade do lojista/conector; validar escopo e avaliação aplicável
com adquirente e profissional qualificado. A fixture de cartão do laboratório não é modelo de
captura de cartão real. Preferir captura/tokenização homologada que minimize dados sensíveis
no Gateway; nunca persistir CVV, corpos de cartão ou segredos em logs/Kafka.

Antes de homologação: segregação dev/hml/prod e tenants; MFA e menor privilégio; tokens Cognito
M2M por serviço/escopo com renovação; mTLS bancário e rotação; secrets fora do Git; TLS interno;
logs de auditoria sem dados sensíveis; inventário/SBOM e varreduras; testes de invasão; política
de retenção/LGPD; restauração de backup comprovada; observabilidade, alertas, runbooks e DR.
Definir SLO, RPO/RTO e executar carga/falhas com volume realista antes de prometer capacidade.

GitHub Actions futuro: build/test/scan por PR; imagem imutável com digest; OIDC para AWS sem
chaves permanentes; ambientes separados e aprovação de promoção; migrations compatíveis e
rollback; smoke tests após implantação. Esta entrega não cria o pipeline de produção.

Conectar contas bancárias ou contratar infraestrutura não constitui licença IP/SCD nem
comprova autorização de parceiros. O modelo comercial, custódia, crédito e serviços ofertados
precisam de validação jurídica/regulatória e contratos próprios; não presumir licenças da Include
ou Placar Play a partir do desenho técnico.

Referências oficiais consultadas em 22/11/2026:
- https://aws.amazon.com/compliance/services-in-scope/PCI/
- https://docs.aws.amazon.com/prescriptive-guidance/latest/aws-security-reference-architecture-pci-data-security-standard-dss/introduction.html
- https://learn.microsoft.com/en-us/azure/compliance/offerings/offering-pci-dss
- https://cloud.google.com/security/compliance/pci-dss
