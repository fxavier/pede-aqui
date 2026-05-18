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
- Prometheus: `http://localhost:9090` scraping backend `/actuator/prometheus`

## Keycloak Realm

Create a local realm named `delivery` and roles:

- `CUSTOMER`
- `VENDOR_ADMIN`
- `VENDOR_STAFF`
- `COURIER`
- `ADMIN`
- `OPS`
- `FINANCE`
- `SUPPORT`

Include a `tenant_id` claim in local test users so the backend can enforce tenant isolation.
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
docker run --rm --network host -v "$PWD":/backup minio/mc:RELEASE.2025-02-20T20-33-30Z \
  sh -c "mc alias set local http://host.docker.internal:9000 minioadmin minioadmin && mc mirror --overwrite local /backup/minio-backup"
```

MinIO restore:

```bash
docker run --rm --network host -v "$PWD":/backup minio/mc:RELEASE.2025-02-20T20-33-30Z \
  sh -c "mc alias set local http://host.docker.internal:9000 minioadmin minioadmin && mc mirror --overwrite /backup/minio-backup local"
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
