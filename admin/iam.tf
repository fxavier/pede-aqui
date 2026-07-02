# Cria o utilizador IAM do backend que assina os presigned URLs. Bootstrap
# privilegiado — corre com credenciais de admin. A política de acesso a
# objectos (PutObject/GetObject/DeleteObject) é anexada no módulo infra/ via
# existing_backend_user, por isso este módulo só cria o principal + a key.
resource "aws_iam_user" "backend" {
  name = var.backend_user_name
  tags = local.tags
}

resource "aws_iam_access_key" "backend" {
  user = aws_iam_user.backend.name
}
