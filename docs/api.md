# API

The backend REST API uses base path `/api/v1`.

## Current Implemented Endpoints

- `GET /api/v1/me`: returns the current authenticated user's profile.
- `POST /api/v1/tenants`: creates a tenant, `ADMIN` only.
- `GET /api/v1/tenants`: lists tenants, `ADMIN` only.
- `GET /api/v1/tenants/{id}`: returns tenant details, `ADMIN` only.
- `PATCH /api/v1/tenants/{id}/status`: updates tenant status, `ADMIN` only.
- `GET /api/v1/search/vendors`: searches tenant-scoped vendors; returns an empty list until vendor persistence is added.
- `POST /api/v1/catalog/products`: creates a vendor product and rejects prohibited fuel listings.
- `GET /api/v1/catalog/vendors/{vendorId}/products`: lists active products for a vendor.
- `PATCH /api/v1/inventory/{inventoryItemId}/stock`: replaces available stock with a non-negative quantity.
- `POST /api/v1/customers/{customerId}/cart/items`: adds an item to a single-vendor cart after stock validation.
- `GET /api/v1/customers/{customerId}/cart/pricing`: calculates subtotal fees, tax, discount, and total.
- `POST /api/v1/checkout`: converts an active cart into an order using a checkout idempotency key.
- `POST /api/v1/payments/{paymentId}/confirm`: confirms a local/mock payment idempotently.
- `GET /api/v1/orders/{orderId}/tracking`: returns order status and the customer delivery confirmation code.
- `POST /api/v1/deliveries/{deliveryId}/complete`: completes delivery when the courier submits the matching 6-digit code.
- `POST /api/v1/vendors`: registers a vendor profile for the authenticated tenant (`VENDOR_ADMIN` or `ADMIN`).
- `GET /api/v1/vendors?available=true|false`: lists tenant vendors and supports availability filter.
- `PATCH /api/v1/vendors/{vendorId}/availability`: updates vendor availability and estimated delivery minutes.
- `PATCH /api/v1/vendors/{vendorId}/verification`: admin verification decision with required reason.
- `PATCH /api/v1/vendor/orders/{orderId}/accept`: vendor accepts an order.
- `PATCH /api/v1/vendor/orders/{orderId}/reject`: vendor rejects an order with required reason.
- `PATCH /api/v1/vendor/orders/{orderId}/preparing`: vendor marks an accepted order as preparing.
- `PATCH /api/v1/vendor/orders/{orderId}/ready-for-pickup`: vendor marks a preparing order as ready for pickup.
- `GET /api/v1/couriers/me`: returns courier profile for authenticated courier user.
- `PATCH /api/v1/couriers/me/availability`: switches courier online/offline availability.
- `GET /api/v1/couriers/me/earnings-summary`: returns completed deliveries, failed deliveries, and earnings total.
- `GET /api/v1/dispatch-jobs`: lists dispatch jobs for courier/ops/admin roles.
- `POST /api/v1/dispatch-jobs/assign?orderId={id}&deliveryId={id}&operatingZoneId={id}`: assigns a delivery to an eligible courier in the requested zone.
- `POST /api/v1/dispatch-jobs/{jobId}/accept`: courier accepts assignment.
- `POST /api/v1/dispatch-jobs/{jobId}/reject`: courier rejects assignment with reason.
- `POST /api/v1/dispatch-jobs/{jobId}/reassign?operatingZoneId={id}`: ops/admin reassign rejected jobs.
- `PATCH /api/v1/deliveries/{deliveryId}/status`: updates delivery lifecycle status and optional proof/COD metadata.
- `GET /api/v1/admin/tenants`: admin tenant visibility endpoint.
- `POST /api/v1/admin/zones` and `GET /api/v1/admin/zones`: admin zone configuration endpoints.
- `PUT /api/v1/admin/policy` and `GET /api/v1/admin/policy`: tenant fee/tax/commission/cancellation policy endpoints.
- `GET /api/v1/admin/audit`: audit trail endpoint for sensitive admin/ops actions.
- `GET /api/v1/ops/dispatch/jobs`: operations dispatch monitor list.
- `GET /api/v1/ops/dispatch/deliveries/{deliveryId}/events`: operations delivery event timeline.
- `POST /api/v1/ops/dispatch/jobs/{jobId}/reassign`: operations reassignment action with `operatingZoneId` payload.
- `GET /api/v1/finance/transactions`: finance view of payment transactions.
- `GET /api/v1/finance/commissions`: finance commission list.
- `GET /api/v1/finance/refunds`: finance refund list.
- `GET /api/v1/finance/cash-reconciliation`: COD reconciliation records.
- `GET /api/v1/finance/payout-status`: pending and settled commission payout totals.
- `GET /api/v1/finance/summary`: finance aggregate summary cards.
- `GET /api/v1/finance/export`: basic export payload for transactions, commissions, refunds, and COD.
- `POST /api/v1/payments/{paymentId}/refunds`: create refund request (idempotent by `idempotencyKey`).
- `POST /api/v1/payments/refunds/{refundId}/approve`: approve/refund action for FINANCE or ADMIN.
- `POST /api/v1/payments/refunds/{refundId}/reject`: reject refund action for FINANCE or ADMIN.
- `POST /api/v1/support/tickets`: customer/support/admin create support ticket linked to optional order.
- `GET /api/v1/support/tickets/mine`: customer-only ticket list with internal notes hidden.
- `GET /api/v1/support/tickets`: support/admin ticket list with internal notes visible.
- `PATCH /api/v1/support/tickets/{ticketId}/classify`: support/admin incident classification update.
- `PATCH /api/v1/support/tickets/{ticketId}/status`: support/admin lifecycle status update.
- `PATCH /api/v1/support/tickets/{ticketId}/internal-note`: support/admin internal note update.
- `PATCH /api/v1/support/tickets/{ticketId}/resolve`: support/admin resolve action.

## Customer Flow Notes

Customer carts are limited to one vendor for the MVP. Checkout reserves stock, creates an order reference, stores the delivery confirmation code hash, creates a mock payment record, and creates a delivery record in one transaction. The customer can see the 6-digit delivery code through order tracking; couriers submit the code to complete delivery, and invalid attempts are counted without logging the submitted code.

OpenAPI UI is available at `/swagger-ui.html` when the backend is running.

## Vendor Onboarding and Fulfillment Notes

Vendor onboarding starts with profile registration and moves through verification statuses `PENDING`, `APPROVED`, and `REJECTED`. Verification decisions require a non-empty reason. Rejected vendors are forced to unavailable status.

Catalog and inventory management are tenant scoped. Vendors can create products (including pharmacy prescription metadata flags), list products by vendor, and update stock with non-negative quantity constraints.

Vendor fulfillment transitions are enforced in service logic:

- Accept: `PAYMENT_CONFIRMED -> ACCEPTED_BY_VENDOR`
- Reject with reason: `PAYMENT_CONFIRMED|ACCEPTED_BY_VENDOR -> CANCELLED`
- Preparing: `ACCEPTED_BY_VENDOR -> PREPARING`
- Ready for pickup: `PREPARING -> READY_FOR_PICKUP`

Example reject payload:

```json
{
  "reason": "Sem capacidade na cozinha"
}
```

## Dispatch and Delivery Flow Notes

Dispatch assignment uses simple selection: the first courier that is `APPROVED`, `available=true`, and in the requested `operatingZoneId`. Reassignment applies the same eligibility rules and excludes the previously assigned courier.

Supported courier status progression for delivery lifecycle:

- `ACCEPTED -> ARRIVED_AT_VENDOR`
- `ARRIVED_AT_VENDOR -> PICKED_UP`
- `PICKED_UP -> ON_ROUTE_TO_CUSTOMER`
- `ON_ROUTE_TO_CUSTOMER -> ARRIVED_AT_CUSTOMER`
- `ARRIVED_AT_CUSTOMER -> FAILED_DELIVERY` or delivery code completion to `DELIVERED`

`PATCH /deliveries/{deliveryId}/status` accepts optional proof and cash metadata:

```json
{
  "status": "FAILED_DELIVERY",
  "proofPhotoStorageKey": "proofs/delivery-123.jpg",
  "cashCollectedAmount": 120.0
}
```

Delivery confirmation code remains a separate secure step at `POST /deliveries/{deliveryId}/complete` with a 6-digit code payload.

## Admin and Operations Workflow Notes

Role boundaries:

- `ADMIN`: policy and zone configuration, tenant governance, audit visibility.
- `OPS`: dispatch monitoring, delivery event visibility, and job reassignment.

Reassignment rule:

- Reassignment requires a `REASSIGNABLE` job and target `operatingZoneId`.
- Eligible courier selection is restricted to couriers that are `APPROVED`, online, and inside the requested zone.

Audit events:

- Sensitive operations actions (e.g., dispatch reassignment) are logged with actor, target, action, result, and timestamp.

## Finance Views Notes

Finance role boundaries:

- Finance and Admin can access finance endpoints.
- Non-finance roles are blocked by role-based access control.

Refund behavior assumptions:

- Refund creation is idempotent by `idempotencyKey`.
- Approve/reject actions are auditable and recorded as sensitive events.
- Duplicate approve/reject requests return stable final refund state when valid.

COD and payout assumptions:

- COD reconciliation tracks unreconciled cash totals by `PENDING` records.
- Payout status is derived from commission statuses (`PENDING`, `SETTLED`) for MVP reporting.

## Support Flow Notes

Ticket lifecycle:

- Customer creates ticket (`OPEN`) optionally linked to `orderId`.
- Support/Admin classify incident and move status (`IN_PROGRESS`, `RESOLVED`, `CANCELLED`).
- Internal notes are restricted to support/admin visibility.

Visibility rules:

- `GET /support/tickets/mine` always excludes internal notes for customer safety.
- `GET /support/tickets` includes internal notes for support/admin workflows.

Audit assumptions:

- Status, classification, internal-note, and resolve actions are recorded as sensitive audit events.

## Contract Alignment Notes

This document is aligned to `specs/001-delivery-marketplace-mvp/contracts/openapi.yaml` using `/api/v1` base path. Endpoint groups map as follows:

- Auth/Tenant: `/me`, `/tenants`
- Vendor/Catalog/Inventory/Search: `/vendors`, `/catalog/*`, `/inventory/*`, `/search/vendors`
- Cart/Checkout/Orders/Payments/Refunds: `/customers/*/cart/*`, `/checkout`, `/orders/*`, `/payments/*`
- Courier/Dispatch/Delivery: `/couriers/*`, `/dispatch-jobs/*`, `/deliveries/*`
- Admin/OPS/Finance/Support/Notifications/Dashboard: `/admin/*`, `/ops/*`, `/finance/*`, `/support/*`, `/notifications`, `/dashboards/*`

When a path evolved during implementation (e.g. vendor order transitions or support flows), this document keeps the implemented `/api/v1` route names and role guards explicit for operational testing.
