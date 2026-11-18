variable "region" { default = "sa-east-1" }
variable "project" { default = "inpay-dev" }
variable "vpc_id" { type = string }
variable "private_subnet_ids" { type = list(string) }
variable "m2m_scopes" {
  type = map(list(string))
  default = {
    payment-core            = ["fraud/evaluate", "routing/select", "connectors-cielo/execute"]
    checkout                = ["payments/create", "payments/read", "links/read"]
    link-expiration-trigger = ["links/write"]
    reconciliation-trigger  = ["reconciliation/run"]
    connector-cielo         = ["tokens/resolve"]
  }
}
