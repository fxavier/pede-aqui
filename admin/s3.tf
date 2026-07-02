# Lê os metadados do bucket existente. Requer credenciais de admin (o
# utilizador do backend não tem permissão de leitura sobre o bucket).
data "aws_s3_bucket" "existing" {
  bucket = var.bucket_name
}
