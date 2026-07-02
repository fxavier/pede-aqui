# Módulo ADMIN — corre com credenciais de admin.
#
# Só lê metadados do bucket existente (ARN/ID/região) e exporta-os, para que
# esses valores possam ser fixados em infra/locals.tf. Assim o módulo infra/
# nunca precisa de uma fase de leitura (o utilizador do backend não tem
# s3:ListBucket/GetBucket*).
#
# Uso:
#   cd admin
#   terraform init
#   terraform apply -var aws_profile=<perfil-admin>
#   # copie bucket_arn / bucket_id / bucket_region para infra/locals.tf

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
  # Use um perfil de ADMIN para ter permissão de leitura do bucket. Um profile
  # aqui tem prioridade sobre as variáveis AWS_* exportadas do .env.
  profile = var.aws_profile != "" ? var.aws_profile : null
}

locals {
  tags = {
    Project     = var.project
    Environment = var.environment
    ManagedBy   = "terraform"
  }
}
