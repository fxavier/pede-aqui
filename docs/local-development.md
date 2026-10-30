# Local Development

## Docker Compose

Start the local dependencies:

```bash
docker compose up -d postgres redis keycloak minio prometheus
```

Services:

- PostgreSQL/PostGIS: `localhost:5432`, database `delivery`, user `delivery`, password `delivery`
- Redis: `localhost:6379`
- Keycloak: `http://localhost:8081`, admin `admin`, password `admin`
- MinIO: API `http://localhost:9000`, console `http://localhost:9001`, user `minioadmin`, password `minioadmin`
- Prometheus: `http://localhost:9090` scraping backend `/actuator/prometheus` on the internal management port `backend:8091` (`MANAGEMENT_SERVER_PORT`, not published on the host)

## Keycloak Realm

Docker Compose imports the realm automatically (`start-dev --import-realm`) from two files:

- `keycloak/delivery-realm.json` — environment-neutral realm (roles, clients, flows, policies, service accounts only);
- `keycloak/dev-users.json` — **dev-only** human users, mounted as `delivery-users-0.json`.

Dev seed users (password policy: ≥10 chars, upper + lower + digit, not the username, last 3 not reusable):

| User | Password | Role | tenant_id |
|---|---|---|---|
| `admin` | `Admin12345` | ADMIN (platform) | — |
| `finance` | `Finance12345` | FINANCE | `5e6d0c1a-…` |
| `support` | `Support12345` | SUPPORT | `5e6d0c1a-…` |
| `ops` | `Ops1234567` | OPS | `5e6d0c1a-…` |

`admin`, `finance` and `ops` must enrol TOTP (authenticator app) on their first **browser** login
(e.g. the account console at `http://localhost:8081/realms/delivery/account`). The backoffice still uses
the password grant, which does not enforce that requirement; but after enrolling TOTP the password grant
for that user requires a `totp` parameter the backoffice does not send — keep a non-enrolled user for
local backoffice work until LAC-10 (PKCE) lands.

Access tokens last 5 minutes and refresh tokens rotate (a refresh token can be used once).

Keycloak only imports a realm that does not exist yet. After changing either file, recreate the container:

```bash
docker compose rm -sf keycloak && docker compose up -d keycloak
```

### Customer web login (`pede-aqui-web`, PKCE only)

The customer web app (`pede-aqui-delivery`, `http://localhost:5173`) logs in **only** with Authorization
Code + PKCE (S256) through the Keycloak login page; the password grant is disabled on `pede-aqui-web`
(`directAccessGrantsEnabled=false`, a ROPC request answers `400 unauthorized_client`). Realm settings for the
client: redirect URIs `http://localhost:5173/*` and `http://localhost:4173/*` (vite preview), web origins for
both, post-logout redirect `http://localhost:5173/` and `http://localhost:4173/`. An existing Keycloak
container keeps the old client (ROPC on) until it is recreated (see above).

- Tokens are held only in memory (oidc-client-ts `InMemoryWebStorage`). A page reload restores the session
  through the Keycloak SSO cookie with a hidden `prompt=none` iframe (`/auth/silent-callback`); a new tab or a
  browser that blocks the iframe's cookies (see below) shows the user as logged out until "Entrar", which then
  completes without a password while the SSO session is alive (30 min idle).
- Self-registration stays in the app (`/register` → `POST /api/v1/customers/register`); afterwards the app
  starts the PKCE login with `login_hint=<email>`, so the user types the password once more on Keycloak.
- To test as a customer, register through `/register` (the dev seed has no customer user).

### Customer web security headers

`vite` and `vite preview` send the headers built by `pede-aqui-delivery/src/lib/security/headers.ts` (the dev
CSP additionally allows the react-refresh inline preamble, the HMR websocket and MinIO images on
`localhost:9000`). Production serves the static bundle from a CDN, so the same headers must be configured
there (CloudFront *response headers policy* or ALB/nginx), with the real API and Keycloak origins:

```text
Content-Security-Policy: default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline' https://fonts.googleapis.com; font-src 'self' https://fonts.gstatic.com; img-src 'self' data: blob: https:; connect-src 'self' https://<api-host> https://<keycloak-host>; frame-src 'self' https://<keycloak-host>; frame-ancestors 'self'; form-action 'self'; base-uri 'self'; object-src 'none'
Strict-Transport-Security: max-age=31536000; includeSubDomains
X-Content-Type-Options: nosniff
X-Frame-Options: SAMEORIGIN
Referrer-Policy: strict-origin-when-cross-origin
Permissions-Policy: geolocation=(self), camera=(), microphone=(), payment=()
```

`frame-ancestors 'self'` / `SAMEORIGIN` (not `none`/`DENY`) is required: the silent-renew callback is the app
loaded in an iframe by itself. HSTS (and `preload`) is a deployment decision — only enable it once every
subdomain is HTTPS. Keep Keycloak on the same registrable domain as the app (e.g. `auth.<domain>` and
`app.<domain>`) so the SSO cookie is first-party for the silent iframe.

For local Swagger or curl testing, authenticated requests may also pass `X-Tenant-Id`
with an active tenant UUID.

## Backend

```bash
cd backend
mvn spring-boot:run
```

Tenant-scoped endpoint example:

```bash
curl -H "Authorization: Bearer <token>" \
  -H "X-Tenant-Id: <tenant-uuid>" \
  http://localhost:8080/api/v1/search/vendors
```

Admin users can create or list tenants through `/api/v1/tenants` without `X-Tenant-Id`.

## Observability (LAC-50)

Opt-in stack — the default `docker compose up` is unchanged:

```bash
docker compose --profile observability up -d otel-collector jaeger grafana   # also starts prometheus
```

- OpenTelemetry Collector: OTLP on `localhost:4317` (gRPC) / `localhost:4318` (HTTP) → forwards traces to Jaeger
  (`observability/otel-collector.yaml`; `docker compose logs otel-collector` shows one line per received batch).
- Jaeger UI: `http://localhost:16686` (service `delivery-marketplace`).
- Grafana: `http://localhost:3030` (anonymous admin; 3000 is the backoffice). Datasources Prometheus + Jaeger and the
  starter dashboard **Pede Aqui — Backend** (HTTP RED, jobs, rate limiting, business metrics, Hikari/JVM) are
  provisioned from `observability/grafana/`.

The backend only **exports** spans when `MANAGEMENT_OTLP_TRACING_ENDPOINT` is set (trace ids are always generated and
logged). Never set it to an empty string — Boot then tries to build an exporter without a URL and startup fails.

- Backend on the host: `MANAGEMENT_OTLP_TRACING_ENDPOINT=http://localhost:4318/v1/traces mvn spring-boot:run`.
  For Prometheus to scrape it too, also set `MANAGEMENT_SERVER_PORT=8091` (job `backend-host` →
  `host.docker.internal:8091`).
- Backend in compose: add `MANAGEMENT_OTLP_TRACING_ENDPOINT=http://otel-collector:4318/v1/traces` to the repo-root
  `.env` (the service loads it via `env_file`); remove it again before running the backend on the host, where that
  hostname does not resolve.
- Sampling: 100 % in the `dev` profile, 10 % otherwise (`TRACING_SAMPLING_PROBABILITY`).
- Logs: readable in `dev` (`[traceId/spanId cid=<correlationId> job=<job>/<runId>]` block); JSON with
  `LOG_FORMAT=logstash` (the default outside `dev`). An error response's `traceId` is the trace to search in Jaeger.

Tear down: `docker compose --profile observability down` (add `-v` to drop volumes).

## Troubleshooting

- If Flyway fails, verify PostgreSQL is healthy and credentials match `application.yml`.
- If authentication fails, verify `KEYCLOAK_ISSUER_URI` matches the realm issuer.
- If a tenant-scoped endpoint returns `tenant_required`, add a `tenant_id` JWT claim
  or pass `X-Tenant-Id` with an active tenant UUID.
- If OpenAPI is unavailable, verify the backend started without database migration errors.

## Backup And Restore

Local PostgreSQL backup:

```bash
docker compose exec postgres pg_dump -U delivery delivery > delivery-local.sql
```

Local PostgreSQL restore:

```bash
docker compose exec -T postgres psql -U delivery delivery < delivery-local.sql
```

MinIO object backup (all buckets):

```bash
docker run --rm --network host -v "$PWD":/backup --entrypoint /bin/sh bitnamilegacy/minio-client:2024.10.8-debian-12-r1 \
  -c "mc alias set local http://host.docker.internal:9000 minioadmin minioadmin && mc mirror --overwrite local /backup/minio-backup"
```

MinIO restore:

```bash
docker run --rm --network host -v "$PWD":/backup --entrypoint /bin/sh bitnamilegacy/minio-client:2024.10.8-debian-12-r1 \
  -c "mc alias set local http://host.docker.internal:9000 minioadmin minioadmin && mc mirror --overwrite /backup/minio-backup local"
```

## End-to-End MVP Flow

Use this smoke flow after seeding tenant/vendor/catalog/courier data:

1. Customer searches vendors: `GET /api/v1/search/vendors`.
2. Customer lists products: `GET /api/v1/catalog/vendors/{vendorId}/products`.
3. Customer adds cart item: `POST /api/v1/customers/{customerId}/cart/items`.
4. Customer checks pricing: `GET /api/v1/customers/{customerId}/cart/pricing`.
5. Customer checkout: `POST /api/v1/checkout` with idempotency key.
6. Customer payment confirm: `POST /api/v1/payments/{paymentId}/confirm`.
7. Vendor accepts and prepares: vendor order transition endpoints.
8. Vendor marks ready-for-pickup.
9. OPS assigns dispatch job.
10. Courier accepts, picks up, and updates delivery statuses.
11. Courier completes with customer 6-digit code.
12. Finance checks summary/refunds/COD endpoints.
13. Support opens ticket linked to order and resolves with internal note.
