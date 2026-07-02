output "bucket_name" {
  description = "Nome do bucket de uploads."
  value       = local.bucket_id
}

output "bucket_arn" {
  description = "ARN do bucket de uploads."
  value       = local.bucket_arn
}

output "aws_region" {
  description = "Região AWS aplicada."
  value       = var.aws_region
}

output "cors_allowed_origins" {
  description = "Origens autorizadas na configuração de CORS."
  value       = var.cors_allowed_origins
}

# Só preenchidos quando create_iam_user = true. Coloque-os no backend/.env
# (ou na raiz .env) como AWS_ACCESS_KEY_ID / AWS_SECRET_ACCESS_KEY.
output "backend_access_key_id" {
  description = "Access key do utilizador IAM do backend (se criado)."
  value       = var.create_iam_user ? aws_iam_access_key.backend[0].id : null
}

output "backend_secret_access_key" {
  description = "Secret key do utilizador IAM do backend (se criado)."
  value       = var.create_iam_user ? aws_iam_access_key.backend[0].secret : null
  sensitive   = true
}
