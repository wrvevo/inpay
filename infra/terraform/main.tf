resource "aws_kms_key" "gateway" {
  description             = "Gateway encryption"
  policy                  = data.aws_iam_policy_document.kms.json
  enable_key_rotation     = true
  deletion_window_in_days = 30
}
resource "aws_cognito_user_pool" "m2m" {
  name                = "${var.project}-m2m"
  deletion_protection = "ACTIVE"
}
resource "aws_cognito_user_pool_domain" "m2m" {
  domain       = var.project
  user_pool_id = aws_cognito_user_pool.m2m.id
}
locals {
  scopes = {
    payments         = ["create", "read", "capture", "void", "refund"]
    links            = ["write", "read"]
    fraud            = ["evaluate", "read"]
    routing          = ["select", "read"]
    tokens           = ["resolve", "read", "write"]
    connectors-cielo = ["execute", "read"]
    reconciliation   = ["run", "read"]
  }
}
resource "aws_cognito_resource_server" "apis" {
  for_each     = local.scopes
  identifier   = each.key
  name         = each.key
  user_pool_id = aws_cognito_user_pool.m2m.id
  dynamic "scope" {
    for_each = each.value
    content {
      scope_name        = scope.value
      scope_description = "${each.key} ${scope.value}"
    }
  }
}
resource "aws_cognito_user_pool_client" "service" {
  for_each                             = var.m2m_scopes
  name                                 = each.key
  user_pool_id                         = aws_cognito_user_pool.m2m.id
  generate_secret                      = true
  allowed_oauth_flows_user_pool_client = true
  allowed_oauth_flows                  = ["client_credentials"]
  allowed_oauth_scopes                 = each.value
  access_token_validity                = 5
  token_validity_units { access_token = "minutes" }
  depends_on = [aws_cognito_resource_server.apis]
}
resource "aws_secretsmanager_secret" "service" {
  for_each                = var.m2m_scopes
  name                    = "${var.project}/${each.key}/oauth"
  kms_key_id              = aws_kms_key.gateway.arn
  recovery_window_in_days = 30
}
resource "aws_secretsmanager_secret_version" "service" {
  for_each      = var.m2m_scopes
  secret_id     = aws_secretsmanager_secret.service[each.key].id
  secret_string = jsonencode({ client_id = aws_cognito_user_pool_client.service[each.key].id, client_secret = aws_cognito_user_pool_client.service[each.key].client_secret })
}
resource "aws_ecs_cluster" "gateway" {
  name = var.project
  setting {
    name  = "containerInsights"
    value = "enabled"
  }
}
resource "aws_cloudwatch_log_group" "services" {
  name              = "/ecs/${var.project}"
  retention_in_days = 30
  kms_key_id        = aws_kms_key.gateway.arn
}
resource "aws_s3_bucket" "settlement" { bucket_prefix = "${var.project}-settlement-" }
resource "aws_s3_bucket_public_access_block" "settlement" {
  bucket                  = aws_s3_bucket.settlement.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}
resource "aws_s3_bucket_server_side_encryption_configuration" "settlement" {
  bucket = aws_s3_bucket.settlement.id
  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm     = "aws:kms"
      kms_master_key_id = aws_kms_key.gateway.arn
    }
  }
}
resource "aws_s3_bucket_versioning" "settlement" {
  bucket = aws_s3_bucket.settlement.id
  versioning_configuration { status = "Enabled" }
}
resource "aws_security_group" "apps" {
  name_prefix = "${var.project}-apps-"
  vpc_id      = var.vpc_id
  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}
resource "aws_security_group" "data" {
  name_prefix = "${var.project}-data-"
  vpc_id      = var.vpc_id
  ingress {
    from_port       = 5432
    to_port         = 5432
    protocol        = "tcp"
    security_groups = [aws_security_group.apps.id]
  }
  ingress {
    from_port       = 9098
    to_port         = 9098
    protocol        = "tcp"
    security_groups = [aws_security_group.apps.id]
  }
}
resource "aws_db_subnet_group" "gateway" {
  name       = var.project
  subnet_ids = var.private_subnet_ids
}
resource "aws_db_instance" "gateway" {
  identifier                    = var.project
  engine                        = "postgres"
  instance_class                = "db.t4g.medium"
  allocated_storage             = 30
  max_allocated_storage         = 100
  storage_encrypted             = true
  kms_key_id                    = aws_kms_key.gateway.arn
  username                      = "gateway_admin"
  manage_master_user_password   = true
  master_user_secret_kms_key_id = aws_kms_key.gateway.arn
  db_subnet_group_name          = aws_db_subnet_group.gateway.name
  vpc_security_group_ids        = [aws_security_group.data.id]
  multi_az                      = true
  publicly_accessible           = false
  backup_retention_period       = 7
  deletion_protection           = true
  skip_final_snapshot           = false
  final_snapshot_identifier     = "${var.project}-final"
}
resource "aws_msk_serverless_cluster" "gateway" {
  cluster_name = var.project
  vpc_config {
    subnet_ids         = var.private_subnet_ids
    security_group_ids = [aws_security_group.data.id]
  }
  client_authentication {
    sasl {
      iam {
        enabled = true
      }
    }
  }
}
output "cognito_issuer" { value = "https://cognito-idp.${var.region}.amazonaws.com/${aws_cognito_user_pool.m2m.id}" }
output "oauth_secret_arns" { value = { for k, v in aws_secretsmanager_secret.service : k => v.arn } }
output "database_endpoint" { value = aws_db_instance.gateway.address }
output "msk_arn" { value = aws_msk_serverless_cluster.gateway.arn }

data "aws_caller_identity" "current" {}
data "aws_iam_policy_document" "kms" {
  statement {
    sid       = "AccountAdministration"
    actions   = ["kms:*"]
    resources = ["*"]
    principals {
      type        = "AWS"
      identifiers = ["arn:aws:iam::${data.aws_caller_identity.current.account_id}:root"]
    }
  }
  statement {
    sid       = "CloudWatchEncryption"
    actions   = ["kms:Encrypt*", "kms:Decrypt*", "kms:ReEncrypt*", "kms:GenerateDataKey*", "kms:Describe*"]
    resources = ["*"]
    principals {
      type        = "Service"
      identifiers = ["logs.${var.region}.amazonaws.com"]
    }
    condition {
      test     = "ArnEquals"
      variable = "kms:EncryptionContext:aws:logs:arn"
      values   = ["arn:aws:logs:${var.region}:${data.aws_caller_identity.current.account_id}:log-group:/ecs/${var.project}"]
    }
  }
}
