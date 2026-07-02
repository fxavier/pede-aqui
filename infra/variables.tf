variable "aws_region" {
  description = "Região AWS do bucket e dos recursos. Deve coincidir com a região real do bucket."
  type        = string
  default     = "eu-west-1"
}

variable "aws_profile" {
  description = <<-EOT
    Perfil AWS (~/.aws) com que o Terraform autentica. Use um perfil de ADMIN
    para poder aplicar CORS e IAM (s3:PutBucketCORS, iam:PutUserPolicy). Um
    profile aqui tem prioridade sobre as variáveis AWS_* exportadas do .env.
    Vazio = cadeia de credenciais padrão.
  EOT
  type        = string
  default     = ""
}

variable "project" {
  description = "Nome do projeto, usado em tags e nomes de recursos."
  type        = string
  default     = "pede-aqui"
}

variable "environment" {
  description = "Ambiente (dev, staging, prod)."
  type        = string
  default     = "dev"
}

variable "bucket_name" {
  description = "Nome do bucket S3 usado para uploads via presigned URL."
  type        = string
}

variable "cors_allowed_origins" {
  description = "Origens (browser) autorizadas a fazer upload directo para o S3."
  type        = list(string)
  default = [
    "http://localhost:3000",
    "http://localhost:5173",
    "http://localhost:5174",
  ]
}

variable "manage_bucket" {
  description = <<-EOT
    Se true, o Terraform cria/gere o bucket (e o bloqueio de acesso público).
    Se false (predefinição), o bucket é assumido como já existente e apenas a
    configuração de CORS é aplicada. Para passar para true sobre um bucket
    existente, faça primeiro: terraform import aws_s3_bucket.uploads[0] <bucket_name>
  EOT
  type        = bool
  default     = false
}

variable "create_iam_user" {
  description = "Se true, cria um utilizador IAM dedicado + access key para o backend assinar presigned URLs."
  type        = bool
  default     = false
}

variable "existing_backend_user" {
  description = <<-EOT
    Nome de um utilizador IAM já existente (ex.: o usado pelo backend) ao qual
    anexar a política de objectos (PutObject/GetObject/DeleteObject). Necessário
    para que o upload via presigned URL seja autorizado. Vazio = não anexar.
    Requer credenciais de admin para o apply.
  EOT
  type        = string
  default     = ""
}
