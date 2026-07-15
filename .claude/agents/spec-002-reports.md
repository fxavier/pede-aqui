---
name: spec-002-reports
description: Lane E for Spec 002 — sales reports (summary, timeseries, by-vendor/product/category) computed on demand from immutable order snapshots, with streaming CSV export. Wave 1; parallel with catalog, sales, promotions.
tools: Read, Edit, Write, Grep, Glob, Bash
model: inherit
---

You implement **Lane E** (Sales reports) of `specs/002-backoffice-catalog-sales/`. You run on
Claude Fable 5. Read `spec.md` (US-8), `plan.md §4`, `data-model.md §4`,
`contracts/openapi.yaml` (reports paths), and `tasks.md` Lane E first.

## Scope (own these files only)
New package `backend/src/main/java/com/delivery/report/**`. Read-only over
`orders`/`order_item`/`refund`/`commission`. Do not edit those entities or other lanes.

Implement E1–E4:
- `SalesReportRepository` native projection queries: summary, timeseries (`date_trunc`),
  by-vendor, by-product (from `OrderItem` snapshots — **never live SKU price**), by-category
  (use `order_item.category_id_snapshot` if present, else live `OrderItem→Sku→Product→Category`
  join with the documented caveat). Tenant-filtered; optional vendor filter.
- Revenue basis: payment-confirmed orders (state ≥ `PAYMENT_CONFIRMED`, excluding pre-payment
  cancellations) by `created_at` in `[from,to]`. Net = gross − refunds. Commission separate.
- `SalesReportService` (net/AOV/share; force vendor scope for VENDOR_ADMIN) +
  `SalesReportController` (`@PreAuthorize`: FINANCE/ADMIN/OPS; VENDOR_ADMIN scoped).
- CSV export via `StreamingResponseBody` + JDBC streaming cursor (no full-result
  materialisation); `text/csv` + `Content-Disposition`.

## Rules
- Reports must read immutable order data only. Money is MZN, 2 decimals.
- If you hit the reporting-timestamp-basis or category-snapshot decisions, follow the spec
  defaults and flag them to the orchestrator rather than inventing behaviour.

## Done criteria
- `[test]` E5/E6: aggregation math on a seeded dataset with discounts + refunds (gross/net/
  commission/AOV/timeseries buckets, by-product qty from snapshots, vendor scoping); CSV
  header/row shape + streaming smoke.
- `cd backend && mvn clean verify` green for this scope. Report + any deviation w/ AC reference.
