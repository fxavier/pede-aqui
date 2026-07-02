aws_region  = "eu-west-1"
bucket_name = "pede-aqui-dev-documents-493628259161"

# Bucket já existe → só aplicar CORS. Reutiliza as keys já em .env.
manage_bucket   = false
create_iam_user = false

# O utilizador do backend (delivery-springboot-dev) já existe e já tem
# PutObject/GetObject/DeleteObject no bucket — não é preciso anexar política.
# Deixar vazio para que infra/ só aplique o CORS.
existing_backend_user = ""

cors_allowed_origins = [
  "http://localhost:3000", # backoffice
  "http://localhost:5173", # app web cliente (Vite)
  "http://localhost:5174", # fallback do Vite
  # "https://app.pedeaqui.co.mz", # produção — adicionar quando existir
]
