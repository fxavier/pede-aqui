# Política de menor privilégio para o backend emitir presigned URLs
# (PUT/GET/DELETE de objectos). Não inclui gestão de CORS — isso é feito
# pelo Terraform, não pela aplicação.
data "aws_iam_policy_document" "uploads_rw" {
  statement {
    sid    = "ObjectReadWrite"
    effect = "Allow"
    actions = [
      "s3:PutObject",
      "s3:GetObject",
      "s3:DeleteObject",
    ]
    resources = ["${local.bucket_arn}/*"]
  }
}

resource "aws_iam_user" "backend" {
  count = var.create_iam_user ? 1 : 0
  name  = "${var.project}-${var.environment}-s3-uploader"
  tags  = local.tags
}

resource "aws_iam_user_policy" "backend" {
  count  = var.create_iam_user ? 1 : 0
  name   = "s3-uploads-rw"
  user   = aws_iam_user.backend[0].name
  policy = data.aws_iam_policy_document.uploads_rw.json
}

resource "aws_iam_access_key" "backend" {
  count = var.create_iam_user ? 1 : 0
  user  = aws_iam_user.backend[0].name
}

# Anexa as permissões de objecto a um utilizador IAM JÁ existente (ex.: o
# utilizador do backend que assina os presigned URLs). Sem isto, o PUT do
# browser é recusado pelo S3 com 403 mesmo depois do CORS estar correcto.
resource "aws_iam_user_policy" "existing_backend" {
  count  = var.existing_backend_user != "" ? 1 : 0
  name   = "s3-uploads-rw"
  user   = var.existing_backend_user
  policy = data.aws_iam_policy_document.uploads_rw.json
}
