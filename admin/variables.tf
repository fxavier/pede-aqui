variable "aws_region" {
  description = "Região AWS do bucket. Deve coincidir com a região real do bucket."
  type        = string
  default     = "eu-west-1"
}

variable "aws_profile" {
  description = <<-EOT
    Perfil AWS (~/.aws) com que o Terraform autentica. Use um perfil de ADMIN
    com permissão de leitura do bucket. Um profile aqui tem prioridade sobre as
    variáveis AWS_* exportadas do .env. Vazio = cadeia de credenciais padrão.
  EOT
  type        = string
  default     = ""
}

variable "bucket_name" {
  description = "Nome do bucket S3 de uploads a inspeccionar."
  type        = string
  default     = "pede-aqui-dev-documents-493628259161"
}

variable "backend_user_name" {
  description = "Nome do utilizador IAM do backend a criar (assina os presigned URLs)."
  type        = string
  default     = "delivery-springboot-dev"
}

variable "project" {
  description = "Nome do projeto, usado em tags."
  type        = string
  default     = "pede-aqui"
}

variable "environment" {
  description = "Ambiente (dev, staging, prod)."
  type        = string
  default     = "dev"
}
