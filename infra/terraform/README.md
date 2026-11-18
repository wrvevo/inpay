# AWS — fundação, sem implantação automática

Terraform cria Cognito M2M (clientes separados e scopes), Secrets Manager, KMS, ECS cluster, CloudWatch, RDS PostgreSQL Multi-AZ, MSK Serverless e S3 privado. Requer VPC/subnets privadas existentes. Os serviços ECS, ALB/API Gateway, WAF/CDN, tasks, IAM de aplicação, RDS Proxy e agendamentos Lambda são descritos em DEPLOYMENT.md e ainda precisam de parâmetros da conta. Não executar apply sem revisar custos e plano.

Usar backend remoto com criptografia e lock. O state contém secrets Cognito; restringir acesso. Criar roles/bancos separados no RDS, com senhas únicas via Secrets Manager; o SQL local com senhas de desenvolvimento NÃO pode ser usado na AWS. Subnets precisam de saída controlada para Cognito/provedores ou VPC endpoints correspondentes.

A chave KMS inclui policy para o principal regional de CloudWatch Logs limitada ao encryption context do log group. A fundação não foi aplicada. Fixar minor de PostgreSQL após verificar versão suportada na região. Revisar retenções e disaster recovery para produção.

Cognito do template é M2M: portal humano com PKCE/MFA requer pool/client separado. Expandir resource servers e grants para módulos adicionais antes do deploy. IDs de clientes recebidos pelas APIs devem ser allowlisted; preencher COGNITO_CLIENT_TENANTS ou COGNITO_TRUSTED_SERVICES conforme finalidade. Nunca repassar X-Merchant-Id externo sem autorização.
