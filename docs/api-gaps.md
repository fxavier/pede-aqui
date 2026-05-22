# API Gaps Report

## Backend APIs Available (70+ endpoints)
See `docs/api.md` for the complete list. The backend has all core endpoints implemented.

## Frontend ↔ Backend Integration Gaps

### pede_aqui_delivery_app

| Missing Integration | Backend API Available | Frontend Implementation | Priority |
|---|---|---|---|
| `GET /api/v1/me` — auth check on app start | ✅ | ❌ Mock `AuthRepository` returns hardcoded user | P1 |
| `GET /api/v1/search/vendors` — search nearby vendors | ✅ | ❌ Mock `CatalogRepository` returns hardcoded data | P1 |
| `GET /api/v1/vendors` — list vendors | ✅ | ❌ Same mock repository | P1 |
| `GET /api/v1/catalog/vendors/{vendorId}/products` — store products | ✅ | ❌ Same mock repository | P1 |
| `POST /api/v1/customers/{customerId}/cart/items` — add to cart | ✅ | ❌ Mock `CartRepository` | P1 |
| `GET /api/v1/customers/{customerId}/cart/pricing` — cart pricing | ✅ | ❌ Not implemented at all | P1 |
| `POST /api/v1/checkout` — place order | ✅ | ❌ Mock implementation | P1 |
| `POST /api/v1/payments/{paymentId}/confirm` — confirm payment | ✅ | ❌ Not connected | P1 |
| `GET /api/v1/orders/{orderId}/tracking` — track order | ✅ | ❌ Mock `OrderRepository` | P1 |
| `PATCH /api/v1/notifications/{notificationId}/read` — notifications | ✅ | ❌ Not implemented | P2 |

### pede_aqui_courier_app

| Missing Integration | Backend API Available | Frontend Implementation | Priority |
|---|---|---|---|
| `GET /api/v1/couriers/me` — courier profile | ✅ | ❌ `RemoteCourierDataSource` has wrong endpoint paths | P1 |
| `GET /api/v1/dispatch-jobs` — list dispatch jobs | ✅ | ❌ Mock `MockCourierDataSource` | P1 |
| `POST /api/v1/dispatch-jobs/{jobId}/accept` — accept job | ✅ | ❌ Mock implementation | P1 |
| `POST /api/v1/dispatch-jobs/{jobId}/reject` — reject job | ✅ | ❌ Mock implementation | P1 |
| `PATCH /api/v1/couriers/me/availability` — toggle availability | ✅ | ❌ Mock implementation | P1 |
| `GET /api/v1/couriers/me/earnings-summary` — earnings | ✅ | ❌ Mock implementation | P1 |
| `PATCH /api/v1/deliveries/{deliveryId}/status` — update delivery | ✅ | ❌ Mock implementation | P1 |
| `POST /api/v1/deliveries/{deliveryId}/complete` — confirm delivery | ✅ | ❌ Wrong endpoint in `RemoteCourierDataSource` | P1 |
| `GET /api/v1/notifications` — list notifications | ✅ | ❌ Mock implementation | P2 |
| `GET /api/v1/dashboards/courier` — courier dashboard | ✅ | ❌ Not implemented | P2 |

### pede-aqui-backoffice

| Missing Integration | Backend API Available | Frontend Implementation | Priority |
|---|---|---|---|
| `GET /api/v1/me` — auth/session | ✅ | ❌ Mock Redux auth slice | P1 |
| `GET /api/v1/dashboards/admin` — admin dashboard | ✅ | ❌ Not implemented | P1 |
| `GET /api/v1/dashboards/vendor` — vendor dashboard | ✅ | ❌ Not implemented | P1 |
| `GET /api/v1/dashboards/finance` — finance dashboard | ✅ | ❌ Not implemented | P1 |
| `GET /api/v1/admin/tenants` — tenant management | ✅ | ❌ Not implemented | P1 |
| `GET /api/v1/admin/audit` — audit logs | ✅ | ❌ Not implemented | P1 |
| `GET /api/v1/admin/zones` — zones | ✅ | ❌ Not implemented | P2 |
| `GET /api/v1/finance/*` — all finance endpoints | ✅ | ❌ Not implemented | P1 |
| `GET /api/v1/support/tickets` — support tickets | ✅ | ❌ Not implemented | P1 |
| `GET /api/v1/ops/dispatch/jobs` — ops dispatch | ✅ | ❌ Not implemented | P1 |
| `GET /api/v1/vendor/orders` — vendor orders | ✅ | ❌ Not implemented | P1 |
| `GET /api/v1/vendors` — vendor management | ✅ | ❌ Not implemented | P2 |
| `GET /api/v1/catalog/vendors/{vendorId}/products` — catalog | ✅ | ❌ Not implemented | P2 |
| `GET /api/v1/notifications` — notifications | ✅ | ❌ Not implemented | P2 |

## Backend Missing Features

| Missing Feature | Details | Priority |
|---|---|---|
| No order list endpoint for customers | `GET /api/v1/orders/customers/{customerId}` | P1 |
| No product search by category | Only vendor-scoped product listing | P2 |
| No image upload for delivery app | Backend has presigned URL, but no direct image URL in product DTOs | P2 |

## Summary

- **Total backend APIs**: 70+
- **APIs connected in any frontend**: 0 (all use mock data)
- **APIs needed for MVP**: ~25 across all 3 apps
- **APIs missing from backend**: ~2 low-priority
- **Documentation created**: screen-mapping.md, api-gaps.md, frontend-backend-integration.md, localization.md
