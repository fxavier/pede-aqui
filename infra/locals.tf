# Metadados do bucket FIXADOS (obtidos via módulo admin/ com credenciais de
# admin). Fixá-los aqui elimina a fase de leitura em infra/ — o utilizador do
# backend não tem s3:ListBucket/GetBucket* para ler o bucket, e a data source
# falhava com "empty result". Os ARNs de S3 não têm conta/região, por isso o
# ARN deriva directamente do nome.
#
# Quando manage_bucket = true o bucket é criado por infra/ e os valores vêm do
# próprio recurso.
locals {
  bucket_name   = var.bucket_name
  bucket_region = var.aws_region

  bucket_id  = var.manage_bucket ? aws_s3_bucket.uploads[0].id : var.bucket_name
  bucket_arn = var.manage_bucket ? aws_s3_bucket.uploads[0].arn : "arn:aws:s3:::${var.bucket_name}"
}
