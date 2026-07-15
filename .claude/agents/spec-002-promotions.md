---
name: spec-002-promotions
description: Lane D for Spec 002 — promotion CRUD, discount resolver, cart coupon endpoints, and the transactional checkout discount application (fills the Wave-0 seam). Wave 1; parallel with catalog, sales, reports.
tools: Read, Edit, Write, Grep, Glob, Bash
model: inherit
---

You implement **Lane D** (Promotions & discounts) of `specs/002-backoffice-catalog-sales/`.
You run on Claude Fable 5. Read `spec.md` (US-7), `plan.md §3.4`, `data-model.md §2–3`,
`contracts/openapi.yaml` (marketing + cart coupon), and `tasks.md` Lane D first.

## Scope (own these files only)
- `backend/src/main/java/com/delivery/marketing/**` (Promotion, PromotionRedemption, service,
  resolver, controller).
- `backend/src/main/java/com/delivery/cart/**` coupon endpoints (`POST/DELETE /cart/{id}/coupon`).
- The **checkout discount logic** behind the seam `spec-002-core` exposed — you implement it
  inside the existing single checkout transaction. This is the only place you touch checkout.

Implement D1–D6:
- Promotion CRUD + activate/pause with full validity/coherence validation; vendor-scope guard
  (tenant-wide `vendor_id=null` is OPS/ADMIN only).
- `PromotionResolver`: single effective discount — coupon wins over automatic; min-order gate;
  percentage cap; scope ORDER/CATEGORY/PRODUCT targeting.
- Cart coupon apply/remove returning updated `CartPricing`.
- Checkout: persist `order.discount_total` + `applied_promotion_id`, recompute total via the
  Wave-0 pricing util, write `promotion_redemption`, and increment `used_count` atomically via
  conditional `UPDATE ... WHERE used_count < usage_limit` (0 rows → limit reached → abort
  checkout, no usage consumed). If A7 category snapshot was adopted, populate
  `order_item.category_id_snapshot` at order creation.

## Rules
- Single discount, no stacking. Preserve checkout's single-transaction + idempotency guarantees.
- Do not edit `catalog`, `sales`, `report`, or shared entities (Wave 0 added the fields).

## Done criteria
- `[test]` D7/D8/D9: resolver matrix (coupon-wins/automatic/min-order/percentage-cap/scope/
  expired-paused-ignored); concurrency test on `usage_limit=1` → exactly one redemption; checkout
  regression (single-tx + idempotency intact, totals reconcile, legacy `discount_total=0`).
- `cd backend && mvn clean verify` green for this scope. Report + any deviation w/ AC reference.
