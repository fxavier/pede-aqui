# Bucket de uploads. Gerido pelo Terraform apenas quando manage_bucket = true.
# Se o bucket já existir e quiser passar a geri-lo:
#   terraform import aws_s3_bucket.uploads[0] <bucket_name>
resource "aws_s3_bucket" "uploads" {
  count  = var.manage_bucket ? 1 : 0
  bucket = var.bucket_name
  tags   = local.tags
}

# bucket_id / bucket_arn são definidos em locals.tf (valores fixados, sem
# fase de leitura). Quando manage_bucket = true vêm do recurso acima.

# A correcção do erro "No 'Access-Control-Allow-Origin' header": autoriza o
# preflight + PUT/GET do browser a partir das origens da aplicação.
resource "aws_s3_bucket_cors_configuration" "uploads" {
  bucket = local.bucket_id

  cors_rule {
    allowed_origins = var.cors_allowed_origins
    allowed_methods = ["PUT", "GET", "HEAD"]
    allowed_headers = ["*"]
    expose_headers  = ["ETag"]
    max_age_seconds = 3000
  }
}

# Mantém o bucket privado — presigned URLs funcionam sem acesso público.
# Só aplicado quando gerimos o bucket, para não alterar definições de um
# bucket existente sem ser pedido.
resource "aws_s3_bucket_public_access_block" "uploads" {
  count                   = var.manage_bucket ? 1 : 0
  bucket                  = aws_s3_bucket.uploads[0].id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_ownership_controls" "uploads" {
  count  = var.manage_bucket ? 1 : 0
  bucket = aws_s3_bucket.uploads[0].id
  rule {
    object_ownership = "BucketOwnerEnforced"
  }
}
