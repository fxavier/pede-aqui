# Pede Aqui Delivery Marketplace

Production-oriented MVP for a multi-tenant delivery marketplace using a simple layered architecture.

## Structure

- `backend/`: Java 21 Spring Boot API with controller, service, repository, entity, dto, mapper, config, exception, and security layers.
- `web/`: Next.js TypeScript backoffice.
- `pede-aqui-delivery/`: React + Vite customer-facing web app (browse, order, track).
- `mobile/`: Flutter customer and courier app shell.
- `docs/`: Practical architecture, API, and local development notes.
- `specs/001-delivery-marketplace-mvp/`: specification, plan, data model, contracts, and tasks.

## Prerequisites

- Java 21
- Maven 3.9+
- Docker and Docker Compose
- Node.js 20+
- Flutter stable with Dart 3.x

## Local Services

```bash
docker compose up -d postgres redis keycloak minio
```

## Backend

```bash
cd backend
mvn spring-boot:run
```

OpenAPI is available at `http://localhost:8080/swagger-ui.html` when the backend is running.

## Web

```bash
cd web
npm install
npm run dev
```

## Mobile

```bash
cd mobile/delivery_app
flutter pub get
flutter run

cd mobile/courier_app
flutter pub get
flutter run
```

Aplicacoes moveis independentes:

- `mobile/delivery_app`: app do cliente (delivery)
- `mobile/courier_app`: app do estafeta (courier)

### Execucao com Keycloak (PKCE)

Use os comandos abaixo para correr as apps com autenticacao Keycloak via `--dart-define`.

#### Delivery app (cliente)

```bash
cd mobile/delivery_app
flutter pub get

# Android
flutter run -d android \
  --dart-define=API_BASE_URL=http://10.0.2.2:8080/api/v1 \
  --dart-define=KEYCLOAK_ISSUER=http://10.0.2.2:8081/realms/delivery \
  --dart-define=KEYCLOAK_CLIENT_ID=pede-aqui-mobile \
  --dart-define=KEYCLOAK_REDIRECT_URI=com.pedeaqui.delivery:/oauthredirect

# iOS
flutter run -d ios \
  --dart-define=API_BASE_URL=http://localhost:8080/api/v1 \
  --dart-define=KEYCLOAK_ISSUER=http://localhost:8081/realms/delivery \
  --dart-define=KEYCLOAK_CLIENT_ID=pede-aqui-mobile \
  --dart-define=KEYCLOAK_REDIRECT_URI=com.pedeaqui.delivery:/oauthredirect
```

#### Courier app (estafeta)

```bash
cd mobile/courier_app
flutter pub get

# Android
flutter run -d android \
  --dart-define=API_BASE_URL=http://10.0.2.2:8080/api/v1 \
  --dart-define=KEYCLOAK_ISSUER=http://10.0.2.2:8081/realms/delivery \
  --dart-define=KEYCLOAK_CLIENT_ID=pede-aqui-courier-mobile \
  --dart-define=KEYCLOAK_REDIRECT_URI=com.pedeaqui.courier:/oauthredirect

# iOS
flutter run -d ios \
  --dart-define=API_BASE_URL=http://localhost:8080/api/v1 \
  --dart-define=KEYCLOAK_ISSUER=http://localhost:8081/realms/delivery \
  --dart-define=KEYCLOAK_CLIENT_ID=pede-aqui-courier-mobile \
  --dart-define=KEYCLOAK_REDIRECT_URI=com.pedeaqui.courier:/oauthredirect
```

Notas:

- Em Android emulator, use `10.0.2.2` para acessar servicos locais da maquina host.
- Em iOS simulator, `localhost` funciona para servicos locais.
- Configure no Keycloak os redirect URIs exatamente como acima para cada client.

## Environment Variables

- `DB_URL`: JDBC URL for PostgreSQL.
- `DB_USERNAME`: PostgreSQL username.
- `DB_PASSWORD`: PostgreSQL password.
- `KEYCLOAK_ISSUER_URI`: Keycloak realm issuer URI.
- `SERVER_PORT`: backend port, default `8080`.
- `SPRING_PROFILES_ACTIVE`: optional Spring profile selector.
- `AWS_S3_BUCKET`: AWS S3 bucket used for direct image uploads.
- `AWS_REGION`: AWS region for the S3 bucket.
- `AWS_S3_PRESIGNED_URL_EXPIRATION_SECONDS`: presigned upload URL lifetime in seconds.
- `AWS_ACCESS_KEY_ID`: AWS access key for local backend runtime.
- `AWS_SECRET_ACCESS_KEY`: AWS secret key for local backend runtime.
- `AWS_SESSION_TOKEN`: optional session token for temporary AWS credentials.
- `NEXT_PUBLIC_API_BASE_URL`: web base URL for backend API, default `http://localhost:8080/api/v1`.
- `NEXT_PUBLIC_APP_TOKEN`: local development token for web pages.
- `API_BASE_URL` (Flutter): backend API URL used by mobile apps.
- `APP_TOKEN` (Flutter courier): local bearer token for courier app.
- `KEYCLOAK_ISSUER` (Flutter): issuer used by mobile PKCE auth.
- `KEYCLOAK_CLIENT_ID` (Flutter): mobile client identifier.
- `KEYCLOAK_REDIRECT_URI` (Flutter): mobile callback URI.
- `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`: local database container settings.
- `MINIO_ROOT_USER`, `MINIO_ROOT_PASSWORD`: local object storage credentials.
- `KEYCLOAK_ADMIN`, `KEYCLOAK_ADMIN_PASSWORD`: local Keycloak admin bootstrap credentials.
- OpenAPI local endpoints: `/api-docs` and `/swagger-ui.html`.

Do not commit real secrets. Use local `.env` files only for developer machines.

## Validation

```bash
docker compose config
cd backend && mvn clean verify
cd web && npm run lint && npm run build
cd mobile && flutter analyze && flutter test
```
