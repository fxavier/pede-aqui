# Spec 002 — Implementation Tasks

Organised into lanes suitable for parallel subagent execution. Lane B (backend contract)
must publish the OpenAPI fragment before Lane F (frontend) consumes it. Migrations (Lane A)
gate all backend service work. `[test]` tasks are non-optional acceptance gates.

Conventions: each new public class carries a one-line responsibility comment; each public
service method a purpose comment; non-obvious business rules commented. `mvn clean verify`
and `npm run validate` must pass per lane.

## Lane A — Database & migrations (gates B)

- **A1** `V<NNN>__catalog_product_image_desc.sql`: add `product.image_storage_key`, `product.updated_at`, and `product.description` *only if absent* (verify entity first).
- **A2** `V<NNN>__sku_pending_price.sql`: add `sku.pending_price`, `pending_price_submitted_at`, `pending_price_submitted_by` + check `pending_price is null or pending_price >= 0`.
- **A3** `V<NNN>__orders_discount.sql`: add `orders.discount_total default 0`, `orders.applied_promotion_id`.
- **A4** `V<NNN>__promotion.sql`: create `promotion`, `promotion_redemption`; partial-unique `(tenant_id, code) where code is not null`; scope/type check constraints; supporting indexes.
- **A5** `V<NNN>__cart_discount.sql`: add `cart.applied_promotion_id`, `cart.coupon_code`, `cart.discount_total default 0`.
- **A6** `V<NNN>__report_indexes.sql`: report/sales supporting indexes.
- **A7** *(conditional, on confirmation)* `V<NNN>__order_item_category_snapshot.sql`: add `order_item.category_id_snapshot`.
- **[test] A8** Flyway migration test: fresh DB migrates clean; `mvn ... -Dtest=*MigrationTest` green; `ddl-auto: validate` passes against updated entities.

## Lane B — Backend: Catalog & moderation (depends A1,A2)

- **B1** Entity updates: `Product` (+description,+imageStorageKey,+updatedAt), `Sku` (+pending fields).
- **B2** `ProductService.updateProduct(id, req)` — partial update; tenant + own-vendor guard; audit `PRODUCT_UPDATED` with field diff.
- **B3** `ProductService.updatePrice(id, price)` — resolve single active SKU (0/>1 → `409`); compute Δ%; branch on `app.catalog.price-review`; reject second pending (`409`); audit `PRODUCT_PRICE_UPDATED`/`PRODUCT_PRICE_PENDING`.
- **B4** `ProductImageService`: `setImage(id, storageKey)` (validate tenant image-prefix ownership), `clearImage(id)`; image URL resolver (presigned GET vs public URL by profile); audit.
- **B5** `PriceModerationService`: `listPending()`, `approve(skuId)` (`price = pending_price`, clear, audit `PRODUCT_PRICE_APPROVED`), `reject(skuId, reason)` (clear, audit `PRODUCT_PRICE_REJECTED`); OPS/ADMIN only.
- **B6** Controllers: `CatalogProductController` (`PATCH product`, `PATCH price`, `PUT/DELETE image`), `PriceModerationController`; `@PreAuthorize` per §3 matrix; request DTO validation.
- **B7** Config props `app.catalog.price-review.{enabled,threshold-percent}` + `@ConfigurationProperties` bean; defaults in `application.yml`.
- **[test] B8** Service tests: price-review branch matrix (disabled/within/over/second-pending `409`/non-single-SKU `409`); tenant & vendor isolation; image storageKey ownership rejection; moderation approve/reject effects.
- **[test] B9** Controller tests: `@PreAuthorize` 403 matrix; validation 400s; VENDOR_ADMIN cross-vendor 403.

## Lane C — Backend: Sales management (depends A3; reuses order/payment/finance/notification)

- **C1** `SalesService.search(filter, pageable)` + `SalesRow`/`SaleDetail` projections over `OrderRepository`; force `vendor_id` for VENDOR_ADMIN; mask PII for SUPPORT.
- **C2** `SalesService.cancel(orderId, reason)` — delegates to existing order state machine; pre-dispatch guard (`409` otherwise); audit `SALE_CANCELLED`.
- **C3** `SalesService.refund(orderId, amount?, reason, idemKey)` — delegates to existing refund/finance path; cap `amount ≤ paid − alreadyRefunded` (`422`); idempotent; audit `SALE_REFUNDED`.
- **C4** `SalesService.resendNotification(orderId, type)` — via `NotificationService`; `DELIVERY_CODE` never returns/logs OTP; audit `SALE_NOTIFICATION_RESENT`.
- **C5** `SalesService.statusOverride(orderId, target, reason, idemKey)` — gated by `app.sales.status-override.enabled` (default false); allow-list matrix; ADMIN/OPS; audit `SALE_STATUS_OVERRIDDEN`.
- **C6** `SalesController` with `@PreAuthorize` per §3; DTOs; idempotency headers on refund/override.
- **[test] C7** Service tests: cancel state-machine guard; refund cap + idempotent retry (same key → same result, no double refund); resend does not leak OTP; override allow-list + gate-off 403; tenant/vendor isolation.
- **[test] C8** Controller tests: role matrix; 409/422 paths.

## Lane D — Backend: Promotions & discount application (depends A3,A4,A5)

- **D1** Entities/repos: `Promotion`, `PromotionRedemption`, `PromotionRepository`, `PromotionRedemptionRepository`.
- **D2** `PromotionService` CRUD + `activate`/`pause`; validity/coherence validation (type↔value, scope↔target, `startsAt<endsAt`, percentage range, tenant-wide requires OPS/ADMIN); vendor-scope guard; audit.
- **D3** `PromotionResolver` — given cart (+ optional coupon code), compute the single effective discount (coupon wins over automatic; min-order gate; percentage cap; scope targeting).
- **D4** Cart integration: `POST/DELETE /cart/{id}/coupon` → attach/detach, recompute `CartPricing`.
- **D5** Checkout integration: inside the **existing single checkout transaction**, resolve discount, persist `order.discount_total` + `applied_promotion_id`, recompute `total = subtotal+fees+taxes−discount`, write `promotion_redemption`, atomically increment `used_count` via conditional `UPDATE ... WHERE used_count < usage_limit` (0 rows → limit reached → abort checkout, no usage consumed).
- **D6** Controllers: `PromotionController`, cart coupon endpoints; `@PreAuthorize`.
- **[test] D7** Resolver tests: coupon-wins, automatic-applies, min-order gate, percentage cap, scope PRODUCT/CATEGORY/ORDER, expired/paused ignored.
- **[test] D8** Concurrency test: two checkouts racing a `usage_limit=1` promotion → exactly one redemption; the other fails cleanly.
- **[test] D9** Checkout regression: single-transaction + idempotency intact; totals reconcile with discount; legacy orders (`discount_total=0`) unaffected.

## Lane E — Backend: Sales reports (depends A3,A6; A7 if adopted)

- **E1** `SalesReportRepository` native projection queries: summary, timeseries (`date_trunc`), by-vendor, by-product (`order_item` snapshots), by-category (live join or `category_id_snapshot`). Tenant-filtered; optional vendor filter; revenue basis = payment-confirmed orders by `created_at`.
- **E2** `SalesReportService` — assemble DTOs, compute net/AOV/shares; force vendor scope for VENDOR_ADMIN.
- **E3** `SalesReportController` — summary/timeseries/by-vendor/by-product/by-category; `@PreAuthorize` (FINANCE/ADMIN/OPS; VENDOR_ADMIN scoped).
- **E4** CSV export via `StreamingResponseBody` + JDBC streaming cursor; `text/csv` + `Content-Disposition`; per-report column sets.
- **[test] E5** Aggregation tests on a seeded dataset with discounts + refunds: gross/net/commission/AOV math; timeseries bucketing; by-product qty from snapshots (not live price); vendor scoping.
- **[test] E6** Export test: CSV header/row shape; streaming (no full materialisation); large-set smoke.

## Lane F — Backoffice (Next.js) (depends B/C/D/E OpenAPI publish)

- **F1** `types.ts` + `services.ts`: extend `catalogService`; add `salesService`, `promotionService`, `reportService`. Contract-align to the OpenAPI fragment.
- **F2** `/catalogo` extend: product edit drawer (attributes + single price + image uploader via existing presigned helper); "price change pending" badge; OPS/ADMIN moderation queue tab (approve/reject).
- **F3** `/sales` (new route + `AppShell`): filterable table (date range, status, vendor, product, provider, search); detail panel; cancel/refund/resend actions with confirm dialogs; RBAC-gated action visibility.
- **F4** `/marketing` extend: promotions list + create/edit form (type/scope/target/validity/limits) + activate/pause.
- **F5** `/reports` (new): date-range picker; summary KPI cards; time-series chart; by-vendor/product/category tables; CSV download button.
- **F6** Route guards + role gating per §3; loading/empty/error/forbidden states (screen-coverage check must pass).
- **[test] F7** Service-client contract tests vs OpenAPI; RBAC visibility tests; report render tests (empty/error).

## Lane G — Cross-cutting & release

- **G1** Audit-action constants centralised; verify every mutation writes `audit_logs`.
- **G2** Extend `scripts/mvp-smoke.sh` (or a new `spec-002-smoke.sh`) to assert new endpoints present in OpenAPI and a happy-path per feature.
- **G3** Docs: update `Usage.md` (new flows) and `CLAUDE.md` route table (`/sales`, `/reports`, catalog edit/moderation).
- **G4** Config review: ship `price-review` enabled with production threshold; keep `status-override` disabled.
- **[test] G5** Full `mvn clean verify` + `npm run validate` (backoffice) + `npm run build`; regression suite green.

## Outstanding decisions before Lane E finalises

- Adopt `order_item.category_id_snapshot` now (stable category reports) vs live-join fallback — see spec §7.
- Reporting timestamp basis: order `created_at` (default) vs paid-at — see plan §4.2.
- Production `price-review.threshold-percent` value.

## Operational note (unrelated to this spec)

The AWS access-key rotation flagged in prior infra work remains an open manual action; ensure
it is closed before any `prod`-profile deploy that exercises the new image-upload path.
