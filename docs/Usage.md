# Usage Guide

This guide describes the end-to-end usage flow for the Delivery Marketplace MVP,
with concrete examples for API, web backoffice, and mobile apps.

## 1. Start the Platform (dev profile)

Prerequisites: Docker + Compose, Java 21, Maven 3.9+, Node 20+, Flutter (Dart 3.x).

### 1.1 Infrastructure

From repository root:

```bash
docker compose up -d postgres redis keycloak minio minio-init
```

`minio-init` creates the `pede-aqui-uploads` bucket the backend uses in dev (idempotent). Bringing up `backend` too (`docker compose up -d`) runs the API in a container on port **8082**; running it from source (below) uses **8080**.

### 1.2 Backend and clients

```bash
# Backend API — runs the `dev` Spring profile by default (MinIO storage, no AWS)
cd backend && mvn spring-boot:run

# Customer web app (React + Vite)
cd pede-aqui-delivery && npm install && npm run dev      # http://localhost:5173

# Backoffice (Next.js)
cd pede-aqui-backoffice && npm ci && npm run dev          # http://localhost:3000

# Mobile customer app
cd pede_aqui_delivery_app && flutter pub get && flutter run

# Mobile courier app
cd pede_aqui_courier_app && flutter pub get && flutter run
```

Useful local URLs:

- Backend API: `http://localhost:8080` (source) / `http://localhost:8082` (compose)
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8080/api-docs`
- Keycloak: `http://localhost:8081`
- MinIO console: `http://localhost:9001` (`minioadmin` / `minioadmin`)
- Prometheus: `http://localhost:9090`

## 1a. Storage Profiles: dev vs prod

Uploads use S3-presigned URLs: the client asks the API for a presigned URL, then PUTs
the file straight to the object store. Which store is used is decided entirely by the
active Spring profile — no Java or client code changes between environments.

| | **dev** (default) | **prod** (`SPRING_PROFILES_ACTIVE=prod`) |
|---|---|---|
| Config file | `backend/src/main/resources/application-dev.yml` | `application-prod.yml` |
| Object store | Local MinIO (`http://localhost:9000`) | Real AWS S3 |
| Bucket | `pede-aqui-uploads` (auto-created by `minio-init`) | `AWS_S3_BUCKET` — **no default, fails fast if unset** |
| Region | `us-east-1` (MinIO ignores it) | `AWS_REGION` — **no default** |
| Credentials | `minioadmin` / `minioadmin` | AWS default chain (instance role) or `AWS_ACCESS_KEY_ID`/`SECRET` |
| Browser CORS | Open by default in MinIO — nothing to do | **Must be applied to the bucket** (see 1c) |

`dev` is the default via `spring.profiles.default` in `application.yml`; you don't set
anything to get it. The `AWS_*` values in the repo-root `.env` are ignored for storage in
dev — they only matter under `prod`.

### Running under the prod profile

```bash
cd backend
SPRING_PROFILES_ACTIVE=prod \
AWS_S3_BUCKET=pede-aqui-prod-uploads \
AWS_REGION=eu-west-1 \
mvn spring-boot:run
# credentials: rely on the instance IAM role in real prod, or export
# AWS_ACCESS_KEY_ID / AWS_SECRET_ACCESS_KEY for a machine without one.
```

If `AWS_S3_BUCKET` or `AWS_REGION` are missing, startup aborts immediately rather than
signing URLs against the wrong bucket. This is deliberate.

Frontends point at prod by building with production env vars instead of the dev defaults:
`pede-aqui-delivery` uses `VITE_API_BASE_URL` / `VITE_KEYCLOAK_*`; `pede-aqui-backoffice`
uses `NEXT_PUBLIC_API_BASE_URL` / `NEXT_PUBLIC_KEYCLOAK_*`. Build with `npm run build`.

## 1c. AWS S3 setup for prod (one-time, privileged)

The backend's runtime AWS identity only has object read/write — it cannot create the
bucket or set CORS. Those are one-time admin actions:

1. **Create the bucket** in the target region with Block Public Access **on**. Either
   `terraform apply` in `infra/` with an admin profile (it also applies CORS + ownership),
   or create it in the S3 console.
2. **Apply CORS** so browser preflight succeeds — the `infra/` Terraform does this, or set
   it manually (bucket → Permissions → CORS) allowing the app origins with `PUT, GET, HEAD`.

Without bucket CORS, presigned PUTs from the browser fail the preflight even though the URL
itself is valid. MinIO (dev) does not have this constraint.

## 2. Authentication and Tenant Scope

The backend is JWT-protected and tenant-aware.

- Include `Authorization: Bearer <token>` in protected requests.
- For tenant-scoped routes, include tenant claim in JWT or header `X-Tenant-Id`.

Example:

```bash
curl -H "Authorization: Bearer <token>" \
  -H "X-Tenant-Id: <tenant-uuid>" \
  http://localhost:8080/api/v1/me
```

## 3. Customer Flow (US1)

### 3.1 Discover vendors

```bash
curl -H "Authorization: Bearer <token>" \
  -H "X-Tenant-Id: <tenant-uuid>" \
  "http://localhost:8080/api/v1/search/vendors?category=grocery&available=true&ratingGte=4.0"
```

### 3.2 Browse catalog

```bash
curl -H "Authorization: Bearer <token>" \
  -H "X-Tenant-Id: <tenant-uuid>" \
  "http://localhost:8080/api/v1/catalog/vendors/<vendorId>/products"
```

### 3.3 Add to cart (single-vendor rule)

```bash
curl -X POST "http://localhost:8080/api/v1/customers/<customerId>/cart/items" \
  -H "Authorization: Bearer <token>" \
  -H "X-Tenant-Id: <tenant-uuid>" \
  -H "Content-Type: application/json" \
  -d '{
    "vendorId": "<vendorId>",
    "skuId": "<skuId>",
    "quantity": 1
  }'
```

### 3.4 Review pricing

```bash
curl -H "Authorization: Bearer <token>" \
  -H "X-Tenant-Id: <tenant-uuid>" \
  "http://localhost:8080/api/v1/customers/<customerId>/cart/pricing"
```

### 3.5 Checkout (idempotent)

```bash
curl -X POST "http://localhost:8080/api/v1/checkout" \
  -H "Authorization: Bearer <token>" \
  -H "X-Tenant-Id: <tenant-uuid>" \
  -H "Content-Type: application/json" \
  -d '{
    "cartId": "<cartId>",
    "fulfillmentType": "DELIVERY",
    "deliveryInstructions": "Tocar campainha",
    "idempotencyKey": "checkout-001"
  }'
```

### 3.6 Confirm payment

```bash
curl -X POST "http://localhost:8080/api/v1/payments/<paymentId>/confirm" \
  -H "Authorization: Bearer <token>" \
  -H "X-Tenant-Id: <tenant-uuid>"
```

### 3.7 Track order and delivery code

```bash
curl -H "Authorization: Bearer <token>" \
  -H "X-Tenant-Id: <tenant-uuid>" \
  "http://localhost:8080/api/v1/orders/<orderId>/tracking"
```

## 4. Vendor Flow (US2)

### 4.1 Register vendor

```bash
curl -X POST "http://localhost:8080/api/v1/vendors" \
  -H "Authorization: Bearer <vendor-admin-token>" \
  -H "X-Tenant-Id: <tenant-uuid>" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Restaurante Maputo",
    "category": "food",
    "phone": "+258840000000"
  }'
```

### 4.2 Process order

```bash
curl -X PATCH "http://localhost:8080/api/v1/vendor/orders/<orderId>/accept" \
  -H "Authorization: Bearer <vendor-token>" \
  -H "X-Tenant-Id: <tenant-uuid>"

curl -X PATCH "http://localhost:8080/api/v1/vendor/orders/<orderId>/preparing" \
  -H "Authorization: Bearer <vendor-token>" \
  -H "X-Tenant-Id: <tenant-uuid>"

curl -X PATCH "http://localhost:8080/api/v1/vendor/orders/<orderId>/ready-for-pickup" \
  -H "Authorization: Bearer <vendor-token>" \
  -H "X-Tenant-Id: <tenant-uuid>"
```

## 5. Courier and Dispatch Flow (US3)

### 5.1 Assign dispatch job (OPS/Admin)

```bash
curl -X POST "http://localhost:8080/api/v1/dispatch-jobs/assign?orderId=<orderId>&deliveryId=<deliveryId>&operatingZoneId=<zoneId>" \
  -H "Authorization: Bearer <ops-token>" \
  -H "X-Tenant-Id: <tenant-uuid>"
```

### 5.2 Courier accepts and updates lifecycle

```bash
curl -X POST "http://localhost:8080/api/v1/dispatch-jobs/<jobId>/accept" \
  -H "Authorization: Bearer <courier-token>" \
  -H "X-Tenant-Id: <tenant-uuid>"

curl -X PATCH "http://localhost:8080/api/v1/deliveries/<deliveryId>/status" \
  -H "Authorization: Bearer <courier-token>" \
  -H "X-Tenant-Id: <tenant-uuid>" \
  -H "Content-Type: application/json" \
  -d '{"status": "PICKED_UP"}'
```

### 5.3 Complete delivery with 6-digit code

```bash
curl -X POST "http://localhost:8080/api/v1/deliveries/<deliveryId>/complete" \
  -H "Authorization: Bearer <courier-token>" \
  -H "X-Tenant-Id: <tenant-uuid>" \
  -H "Content-Type: application/json" \
  -d '{"code":"123456"}'
```

## 6. Admin and Operations Flow (US4)

Common actions:

- Manage tenants: `GET/POST /api/v1/tenants`, `PATCH /api/v1/tenants/{id}/status`
- Configure zones/policies: `/api/v1/admin/zones`, `/api/v1/admin/policy`
- Monitor and reassign dispatch: `/api/v1/ops/dispatch/jobs`, `/api/v1/ops/dispatch/jobs/{jobId}/reassign`
- Check audit: `GET /api/v1/admin/audit`

Example reassignment:

```bash
curl -X POST "http://localhost:8080/api/v1/ops/dispatch/jobs/<jobId>/reassign" \
  -H "Authorization: Bearer <ops-token>" \
  -H "X-Tenant-Id: <tenant-uuid>" \
  -H "Content-Type: application/json" \
  -d '{"operatingZoneId":"<zoneId>"}'
```

## 7. Finance Flow (US5)

Useful endpoints:

- `GET /api/v1/finance/summary`
- `GET /api/v1/finance/transactions`
- `GET /api/v1/finance/commissions`
- `GET /api/v1/finance/refunds`
- `GET /api/v1/finance/cash-reconciliation`
- `GET /api/v1/finance/payout-status`
- `GET /api/v1/finance/export`

Approve refund example:

```bash
curl -X POST "http://localhost:8080/api/v1/payments/refunds/<refundId>/approve" \
  -H "Authorization: Bearer <finance-token>" \
  -H "X-Tenant-Id: <tenant-uuid>"
```

## 7a. Backoffice Catalog, Sales, Promotions and Reports (Spec 002)

Backoffice pages: `/catalogo` (edit drawer + moderation tab), `/sales`, `/marketing`
(promotions), `/reports`. The equivalent API flows:

### 7a.1 Catalog edit and price review

```bash
# Partial attribute update (VENDOR_ADMIN own vendor, OPS, ADMIN)
curl -X PATCH "http://localhost:8080/api/v1/catalog/products/<productId>" \
  -H "Authorization: Bearer <token>" -H "X-Tenant-Id: <tenant-uuid>" \
  -H "Content-Type: application/json" \
  -d '{"name":"Novo nome","description":"..."}'

# Single-SKU price change; over app.catalog.price-review.threshold-percent (20%) the
# response returns reviewRequired=true and the product keeps selling at the old price
curl -X PATCH "http://localhost:8080/api/v1/catalog/products/<productId>/price" \
  -H "Authorization: Bearer <token>" -H "X-Tenant-Id: <tenant-uuid>" \
  -H "Content-Type: application/json" \
  -d '{"price": 250.00}'

# Image: upload via /uploads/images/presigned-url first, then link the storageKey
curl -X PUT "http://localhost:8080/api/v1/catalog/products/<productId>/image" \
  -H "Authorization: Bearer <token>" -H "X-Tenant-Id: <tenant-uuid>" \
  -H "Content-Type: application/json" \
  -d '{"storageKey":"tenants/<tenant-uuid>/uploads/<file>"}'
```

### 7a.2 Price moderation (OPS/ADMIN)

```bash
curl -H "Authorization: Bearer <ops-token>" -H "X-Tenant-Id: <tenant-uuid>" \
  "http://localhost:8080/api/v1/catalog/moderation/price-changes"

curl -X POST "http://localhost:8080/api/v1/catalog/moderation/price-changes/<skuId>/approve" \
  -H "Authorization: Bearer <ops-token>" -H "X-Tenant-Id: <tenant-uuid>"

curl -X POST "http://localhost:8080/api/v1/catalog/moderation/price-changes/<skuId>/reject" \
  -H "Authorization: Bearer <ops-token>" -H "X-Tenant-Id: <tenant-uuid>" \
  -H "Content-Type: application/json" -d '{"reason":"Aumento excessivo"}'
```

### 7a.3 Sales management

```bash
# Filterable search (date range, status, vendor, product, provider, free text)
curl -H "Authorization: Bearer <token>" -H "X-Tenant-Id: <tenant-uuid>" \
  "http://localhost:8080/api/v1/sales/orders?from=2026-07-01T00:00:00Z&status=DELIVERED&page=0&size=20"

# Cancel (pre-dispatch only), refund (capped at paid − already refunded, idempotent),
# resend notification (DELIVERY_CODE OTP is never echoed back or logged)
curl -X POST "http://localhost:8080/api/v1/sales/orders/<orderId>/cancel" \
  -H "Authorization: Bearer <token>" -H "X-Tenant-Id: <tenant-uuid>" \
  -H "Content-Type: application/json" -d '{"reason":"Cliente desistiu"}'

curl -X POST "http://localhost:8080/api/v1/sales/orders/<orderId>/refund" \
  -H "Authorization: Bearer <finance-token>" -H "X-Tenant-Id: <tenant-uuid>" \
  -H "Idempotency-Key: refund-001" \
  -H "Content-Type: application/json" -d '{"amount": 100.00, "reason":"Item em falta"}'

curl -X POST "http://localhost:8080/api/v1/sales/orders/<orderId>/resend-notification" \
  -H "Authorization: Bearer <token>" -H "X-Tenant-Id: <tenant-uuid>" \
  -H "Content-Type: application/json" -d '{"type":"CONFIRMATION"}'
```

`POST /sales/orders/{orderId}/status-override` (ADMIN/OPS) is gated by
`app.sales.status-override.enabled` — shipped **disabled**; it returns 403 until enabled.

### 7a.4 Promotions and coupons

```bash
# Create (DRAFT), then activate; coupon promotions carry a code, automatic ones do not
curl -X POST "http://localhost:8080/api/v1/marketing/promotions" \
  -H "Authorization: Bearer <token>" -H "X-Tenant-Id: <tenant-uuid>" \
  -H "Content-Type: application/json" \
  -d '{"name":"Semana da pizza","code":"PIZZA10","type":"PERCENTAGE","value":10,
       "scope":"ORDER","startsAt":"2026-07-01T00:00:00Z","endsAt":"2026-07-31T23:59:59Z",
       "usageLimit":100,"perCustomerLimit":1}'

curl -X POST "http://localhost:8080/api/v1/marketing/promotions/<promotionId>/activate" \
  -H "Authorization: Bearer <token>" -H "X-Tenant-Id: <tenant-uuid>"

# Customer attaches/removes a coupon on the cart; checkout re-validates it, applies the
# single best discount (coupon wins over automatic) and records the redemption atomically
curl -X POST "http://localhost:8080/api/v1/cart/<cartId>/coupon" \
  -H "Authorization: Bearer <customer-token>" -H "X-Tenant-Id: <tenant-uuid>" \
  -H "Content-Type: application/json" -d '{"code":"PIZZA10"}'
```

Order totals always reconcile as `total = subtotal + fees + taxes − discount_total`;
legacy orders read as `discount_total = 0`.

### 7a.5 Sales reports and CSV export (FINANCE/ADMIN/OPS; VENDOR_ADMIN scoped)

```bash
curl -H "Authorization: Bearer <finance-token>" -H "X-Tenant-Id: <tenant-uuid>" \
  "http://localhost:8080/api/v1/reports/sales/summary?from=2026-07-01T00:00:00Z&to=2026-07-15T23:59:59Z"

# Also: /timeseries?interval=day|week|month, /by-vendor, /by-product, /by-category
curl -OJ -H "Authorization: Bearer <finance-token>" -H "X-Tenant-Id: <tenant-uuid>" \
  "http://localhost:8080/api/v1/reports/sales/export?report=summary&from=2026-07-01T00:00:00Z&to=2026-07-15T23:59:59Z"
```

Figures are computed from `order_item` snapshots taken at checkout, so later price or
category changes never rewrite historical reports.

## 8. Support Flow (US6)

### 8.1 Create ticket

```bash
curl -X POST "http://localhost:8080/api/v1/support/tickets" \
  -H "Authorization: Bearer <customer-token>" \
  -H "X-Tenant-Id: <tenant-uuid>" \
  -H "Content-Type: application/json" \
  -d '{
    "orderId":"<orderId>",
    "title":"Entrega atrasada",
    "description":"Pedido chegou muito depois do previsto"
  }'
```

### 8.2 Classify, update, resolve

```bash
curl -X PATCH "http://localhost:8080/api/v1/support/tickets/<ticketId>/classify" \
  -H "Authorization: Bearer <support-token>" \
  -H "X-Tenant-Id: <tenant-uuid>" \
  -H "Content-Type: application/json" \
  -d '{"classification":"DELIVERY_INCIDENT"}'

curl -X PATCH "http://localhost:8080/api/v1/support/tickets/<ticketId>/internal-note" \
  -H "Authorization: Bearer <support-token>" \
  -H "X-Tenant-Id: <tenant-uuid>" \
  -H "Content-Type: application/json" \
  -d '{"internalNote":"Courier report checked, customer compensated"}'

curl -X PATCH "http://localhost:8080/api/v1/support/tickets/<ticketId>/resolve" \
  -H "Authorization: Bearer <support-token>" \
  -H "X-Tenant-Id: <tenant-uuid>"
```

## 9. Notifications and Dashboards

- Notifications list: `GET /api/v1/notifications`
- Mark as read: `POST /api/v1/notifications/{notificationId}/read`
- Dashboards:
  - `GET /api/v1/dashboards/admin`
  - `GET /api/v1/dashboards/vendor`
  - `GET /api/v1/dashboards/courier`
  - `GET /api/v1/dashboards/finance`

## 10. Web and Mobile Usage Summary

### Web Backoffice (`pede-aqui-backoffice`)

Role-based pages behind Keycloak login. Platform super-admins (ADMIN, no tenant) land on
`/platform` to manage and impersonate tenants; tenant users land on `/` (dashboard). Main
routes: `/empresa`, `/admin`, `/catalogo`, `/vendors`, `/users`, `/orders`, `/sales`,
`/couriers`, `/finance`, `/reports`, `/support`, `/marketing`. All pages include loading,
empty, error, and forbidden views.

### Customer Web App (`pede-aqui-delivery`)

Public browse (home, vendor catalog, vertical listing) works without login; cart, checkout,
and order history require a customer token. Auth is ROPC against Keycloak (no redirect).

### Mobile Customer App

Flow in app tabs:

1. Lojas (vendor discovery)
2. Catalogo
3. Carrinho
4. Moradas
5. Checkout
6. Pedidos

### Mobile Courier App

Flow in app:

1. Toggle online/offline
2. View assignments
3. Accept/reject job
4. Update delivery statuses
5. Complete with customer code
6. View earnings summary

## 11. Smoke Validation

Run the MVP smoke script:

```bash
chmod +x scripts/mvp-smoke.sh
scripts/mvp-smoke.sh
```

It validates:

- Backend health endpoint
- OpenAPI endpoint availability
- Critical MVP paths presence in OpenAPI

Run the Spec-002 smoke script (dev stack only — compose postgres/keycloak/minio +
`mvn spring-boot:run`):

```bash
scripts/spec-002-smoke.sh                          # OpenAPI checks + happy paths
SPEC002_SMOKE_OPENAPI_ONLY=1 scripts/spec-002-smoke.sh   # OpenAPI checks only
```

It validates the catalog edit/moderation, sales, promotions, cart-coupon, and report
endpoints in OpenAPI, then (with the seeded dev Keycloak admin and an existing tenant)
runs a happy path per feature: moderation queue list, sales search, promotion
create/list/delete, report summary, and CSV export headers.
