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

variable "marketplace_portals" {
  description = "Portais OAuth opcionais. Definir URLs HTTPS reais antes de aplicar; PKCE no cliente."
  type = map(object({ callbacks = list(string), logout_urls = list(string) }))
  default = {}
  validation {
    condition = alltrue([for key, portal in var.marketplace_portals : contains(["admin", "customer"], key) && length(portal.callbacks) > 0 && alltrue([for url in concat(portal.callbacks, portal.logout_urls) : startswith(url, "https://")])])
    error_message = "Use admin/customer e URLs HTTPS sem curingas operacionais."
  }
}
