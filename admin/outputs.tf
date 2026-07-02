# Valores a fixar em infra/locals.tf.
output "bucket_id" {
  description = "ID (nome) do bucket."
  value       = data.aws_s3_bucket.existing.id
}

output "bucket_arn" {
  description = "ARN do bucket."
  value       = data.aws_s3_bucket.existing.arn
}

output "bucket_region" {
  description = "Região real do bucket."
  value       = data.aws_s3_bucket.existing.region
}

# Credenciais do utilizador do backend. Coloque em .env (raiz) como
# AWS_ACCESS_KEY_ID / AWS_SECRET_ACCESS_KEY. O secret só é revelado uma vez.
output "backend_user_name" {
  description = "Nome do utilizador IAM do backend criado."
  value       = aws_iam_user.backend.name
}

output "backend_access_key_id" {
  description = "Access key ID do utilizador do backend."
  value       = aws_iam_access_key.backend.id
}

output "backend_secret_access_key" {
  description = "Secret access key do utilizador do backend."
  value       = aws_iam_access_key.backend.secret
  sensitive   = true
}
