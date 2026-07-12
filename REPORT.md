# Remediation Execution Report — branch `remediation/plan-2026-07-12`

**Baseline:** `e027a96` (clean tree verified) · **Started:** 2026-07-12 · **Mode:** autonomous, per approved PLAN.md + pre-approved decision list.

## Pre-approved decisions applied
- New deps allowed: `flutter_secure_storage` (both Flutter apps), `vitest` + testing-library (web apps). Nothing else.
- Batch 13: cap-only (array shape kept, max page size 100).
- Batch 10: prod fail-fast; dev defaults move to `application-dev.yml`.
- Batch 16: `USE_MOCK_DATA` default NOT flipped; release builds fail loudly when mock mode is on.
- Batch 11: Prometheus = permit-internal (port not published).
- Skipped by instruction: Keycloak realm hardening (SEC-08), any Flyway migration (V028+), `quantityReserved` data correction, all "Deferred" items, PROD-11 repo hygiene.

## Batch status

| Batch | Lane | Status | Commit / reason |
|---|---|---|---|
| 1 — Courier ownership + OTP lockout + guards | BE | PENDING | |
| 3 — Refund wiring + rejection sequence | BE | PENDING | |
| 4 — Inventory release/settle | BE | PENDING | |
| 5 — Checkout idempotency + 409 mapping | BE | PENDING | |
| 6 — Product approval in cart/checkout | BE | PENDING | |
| 7 — Dispatch assignment guards | BE | PENDING | |
| 9 — Small backend hardening set | BE | PENDING | |
| 10 — Secret defaults out, prod fail-fast | BE | PENDING | |
| 11 — Timeouts + Prometheus scrape + healthchecks | BE | PENDING | |
| 13 — List caps (cap-only) | BE | PENDING | |
| 2 — Vendor ownership (after investigation) | BE | PENDING | |
| 17 — Testcontainers integration baseline | BE | PENDING | |
| 8 — Tenant header hardening (last) | BE | PENDING | |
| 14 — Backoffice route guards + multi-role | BO | PENDING | |
| 15-BO — 401 handling, cache clear, dead 404, logout | BO | PENDING | |
| 18-BO — Test harness (role guards) | BO | PENDING | |
| 15-DW — 401 handling, cart errors, checkout redirect | DW | PENDING | |
| 18-DW — Test harness (cart-slice, idempotency key) | DW | PENDING | |
| 16 — Flutter live-mode enablement (amended) | FL | PENDING | |
| 12 — CI covers real apps | CI | PENDING | |
| 12b — Add npm test steps post-Batch-18 | CI | PENDING | orchestrator, after BO/DW join |

## Skips / blockers

(none yet)

## Pending manual verification

(collected as batches complete)

## Still-open user actions (restated from PLAN.md — NOT done here)
1. Rotate the AWS IAM key pair referenced by `.env:11-12` and the key in `delivery-springboot-dev_accessKeys.csv`; move both files out of the working tree.
2. Rotate the Keycloak admin client secret and DB password wherever the committed defaults were used.
3. Keycloak realm hardening (SEC-08) — skipped by instruction, remains open.
