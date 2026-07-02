# Terraform para resolver o bloqueio de CORS no upload directo (browser → S3
# via presigned URL) usado pelo backoffice/backend "Pede Aqui".
#
# Uso:
#   cd infra
#   terraform init
#   terraform apply
#
# Por defeito (ver terraform.tfvars) o bucket já existe, por isso só é criada a
# configuração de CORS. Para o Terraform passar a gerir o bucket, ver a nota em
# s3.tf (manage_bucket = true + terraform import).

terraform {
  required_version = ">= 1.5"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
}

provider "aws" {
  region = var.aws_region
  # Setting profile here takes precedence over AWS_* env vars, so Terraform can
  # run as an admin profile even when the shell has the app user's keys sourced
  # from .env. Leave empty to use the default credential chain (env / default profile).
  profile = var.aws_profile != "" ? var.aws_profile : null
}

locals {
  tags = {
    Project     = var.project
    Environment = var.environment
    ManagedBy   = "terraform"
  }
}
