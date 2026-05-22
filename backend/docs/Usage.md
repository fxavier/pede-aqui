# Usage Guide

This guide describes the end-to-end usage flow for the Delivery Marketplace MVP,
with concrete examples for API, web backoffice, and mobile apps.

## 1. Start the Platform

From repository root:

```bash
docker compose up -d
```

Then start clients as needed:

```bash
# Web backoffice
cd web && npm run dev

# Mobile customer app
cd mobile/delivery_app && flutter run

# Mobile courier app
cd mobile/courier_app && flutter run
```

Useful local URLs:

- Backend API: `http://localhost:8080`
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8080/api-docs`
- Keycloak: `http://localhost:8081`
- Prometheus: `http://localhost:9090`

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

### Web Backoffice

The web app exposes role-based pages:

- `/admin`
- `/vendor`
- `/operations`
- `/finance`
- `/support`

All pages include loading, empty, error, and forbidden views.

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
