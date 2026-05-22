# Frontend ↔ Backend Integration Guide

## Overview

This document describes how the three frontend applications connect to the backend Spring Boot API.

## Authentication

### Backend Auth Mechanism
- **Provider**: Keycloak (OAuth2 / OpenID Connect)
- **Type**: JWT Bearer token
- **Issuer URL**: `http://localhost:8081/realms/delivery`
- **Roles**: `CUSTOMER`, `VENDOR_ADMIN`, `VENDOR_STAFF`, `COURIER`, `ADMIN`, `OPS`, `FINANCE`, `SUPPORT`

### Token Flow
1. User authenticates via Keycloak (login form or redirect)
2. Receives JWT access token
3. Frontend sends token in `Authorization: Bearer <token>` header
4. Backend validates JWT, extracts roles and tenant context

### Frontend Token Handling

#### Flutter Apps (delivery + courier)
- Token stored in secure storage (flutter_secure_storage)
- `ApiClient` attaches token via `setAuthToken()`
- Token refresh handled by interceptor
- Unauthenticated → redirect to login

#### Next.js Backoffice
- Token stored in HTTP-only cookie or memory
- `http-client.ts` accepts authToken parameter
- Future: NextAuth.js with Keycloak provider

## API Client Setup

### Base URL
- Development: `http://localhost:8080/api/v1`
- Production: Configurable via environment variable

### Common Headers
```http
Content-Type: application/json
Accept: application/json
Authorization: Bearer <jwt_token>
X-Tenant-Id: <tenant_uuid>  # Only if not in JWT
X-Correlation-Id: <uuid>     # Auto-generated
```

### Error Response Format
```json
{
  "code": "VALIDATION_ERROR",
  "message": "User-friendly error description",
  "fieldErrors": [
    {"field": "email", "message": "Email inválido"}
  ],
  "correlationId": "uuid"
}
```

### Pagination
Not yet implemented in backend MVP — all list endpoints return full arrays.

## Data Flow Pattern

### Flutter (BLoC/Cubit)
```
Screen (UI) 
  → Cubit (state management) 
    → Repository (data abstraction) 
      → DataSource (remote/mock)
        → ApiClient (HTTP)
```

### Next.js (Redux Toolkit + React Query)
```
Page/Component
  → React Query Hook (useQuery/useMutation)
    → Service function (typed API call)
      → httpClient (fetch wrapper)
```

## API Endpoint Groups

| Group | Base Path | Roles |
|---|---|---|
| Profile | `/api/v1/me` | All authenticated |
| Tenants | `/api/v1/tenants` | ADMIN |
| Vendors | `/api/v1/vendors` | VENDOR_ADMIN, ADMIN |
| Catalog | `/api/v1/catalog` | All authenticated |
| Inventory | `/api/v1/inventory` | VENDOR_ADMIN, VENDOR_STAFF |
| Search | `/api/v1/search` | All authenticated |
| Cart | `/api/v1/customers/{id}/cart` | CUSTOMER |
| Checkout | `/api/v1/checkout` | CUSTOMER |
| Orders | `/api/v1/orders` | All (scoped) |
| Payments | `/api/v1/payments` | CUSTOMER, FINANCE, ADMIN |
| Courier | `/api/v1/couriers` | COURIER |
| Dispatch | `/api/v1/dispatch-jobs` | COURIER, OPS, ADMIN |
| Delivery | `/api/v1/deliveries` | COURIER, OPS, ADMIN |
| Notifications | `/api/v1/notifications` | All authenticated |
| Support | `/api/v1/support/tickets` | CUSTOMER, SUPPORT, ADMIN |
| Finance | `/api/v1/finance` | FINANCE, ADMIN |
| Dashboards | `/api/v1/dashboards` | Role-specific |
| Admin | `/api/v1/admin` | ADMIN |
| Upload | `/api/v1/uploads` | All authenticated |

## Status Code Mappings

| Order Status | Display Label | Color |
|---|---|---|
| PENDING | Pendente | Gray |
| PAYMENT_PENDING | Pagamento Pendente | Yellow |
| PAYMENT_CONFIRMED | Pagamento Confirmado | Green |
| ACCEPTED_BY_VENDOR | Aceite pelo Vendedor | Green |
| REJECTED_BY_VENDOR | Rejeitado | Red |
| PREPARING | Em Preparo | Blue |
| READY_FOR_PICKUP | Pronto para Recolha | Green |
| DISPATCH_PENDING | Aguarda Estafeta | Yellow |
| ASSIGNED_TO_COURIER | Atribuído ao Estafeta | Blue |
| PICKED_UP | Recolhido | Blue |
| DELIVERING | Em Entrega | Blue |
| DELIVERED | Entregue | Green |
| CANCELLED | Cancelado | Red |
| REFUND_PENDING | Reembolso Pendente | Yellow |
| REFUNDED | Reembolsado | Green |

## Running the Full Stack

```bash
# 1. Start infrastructure
docker compose up -d

# 2. Start backend
cd backend && mvn spring-boot:run

# 3. Start next.js backoffice
cd pede-aqui-backoffice && npm run dev

# 4. Start Flutter delivery app
cd pede_aqui_delivery_app && flutter run

# 5. Start Flutter courier app
cd pede_aqui_courier_app && flutter run
```
