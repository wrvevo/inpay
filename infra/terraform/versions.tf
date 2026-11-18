terraform {
  required_version = ">= 1.8, < 2.0"
  required_providers {
    aws = { source = "hashicorp/aws", version = "~> 5.100" }
  }
}
provider "aws" { region = var.region }
