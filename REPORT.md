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
| 1 — Courier ownership + OTP lockout + guards | BE | SKIPPED (lane held) | pre-existing red gate, see "Skips / blockers" |
| 3 — Refund wiring + rejection sequence | BE | SKIPPED (lane held) | same |
| 4 — Inventory release/settle | BE | SKIPPED (lane held) | same |
| 5 — Checkout idempotency + 409 mapping | BE | SKIPPED (lane held) | same |
| 6 — Product approval in cart/checkout | BE | SKIPPED (lane held) | same |
| 7 — Dispatch assignment guards | BE | SKIPPED (lane held) | same |
| 9 — Small backend hardening set | BE | SKIPPED (lane held) | same (Batch 9 would fix baseline failure #3) |
| 10 — Secret defaults out, prod fail-fast | BE | SKIPPED (lane held) | same |
| 11 — Timeouts + Prometheus scrape + healthchecks | BE | SKIPPED (lane held) | same |
| 13 — List caps (cap-only) | BE | SKIPPED (lane held) | same |
| 2 — Vendor ownership (after investigation) | BE | SKIPPED (lane held) | same; linkage investigation not reached |
| 17 — Testcontainers integration baseline | BE | SKIPPED (lane held) | same |
| 8 — Tenant header hardening (last) | BE | SKIPPED (lane held) | same |
| 14 — Backoffice route guards + multi-role | BO | SKIPPED (lane held) | pre-existing red gate: `npm run lint` fails (see "Skips / blockers") |
| 15-BO — 401 handling, cache clear, dead 404, logout | BO | SKIPPED (lane held) | same |
| 18-BO — Test harness (role guards) | BO | SKIPPED (lane held) | same |
| 15-DW — 401 handling, cart errors, checkout redirect | DW | DONE | `45058db` — 401 interceptor → clear auth + `/login?redirect=…`; cart error banners (vendor page + CartDrawer); checkout redirect moved to effect; shared `logout()` with `signoutRedirect` when an id_token exists |
| 18-DW — Test harness (cart-slice, idempotency key) | DW | DONE | `86cb662` — vitest+jsdom+testing-library (dev-only), `useIdempotencyKey()` extracted, 11 tests green |
| 16 — Flutter live-mode enablement (amended) | FL | DONE | `41531ec` — release-build mock guard (default NOT flipped), `KEYCLOAK_ISSUER` dart-define, `flutter_secure_storage` token persistence + session restore, delivery registration wired to `/customers/register`, real customer id into cart (JWT sub via `/me`), honest earnings parsing. 16 new tests, both gates green |
| 12 — CI covers real apps | CI | DONE | `75aae6f` — 5 jobs (backend, backoffice, delivery-web, 2× Flutter), maven/npm/flutter caching, push filtered to `main` + all PRs; NEXT_PUBLIC_* build placeholders (public localhost values) |
| 12b — Add npm test steps post-Batch-18 | CI | PARTIAL/DONE | `83eaf43` — `npm test` added to the delivery-web job only (11 tests verified green locally). Backoffice test step deferred: batch 18-BO was skipped, no test script exists there |

## Skips / blockers

### Lane BE held in full — backend gate red at the committed baseline (`e027a96`)

`cd backend && mvn clean verify` fails BEFORE any remediation change (verified twice: by the Batch 1 agent and independently by the orchestrator). `Tests run: 50, Failures: 3, Errors: 1`:

1. `CartSecurityApiTest.preventsCustomerFromAddingToAnotherCustomersCart:94` — expected 403, got 200. Root cause: `CartController.addItem` (`CartController.java:29`) binds the `{customerId}` path variable but never compares it to the authenticated caller; `CartService.addItem` resolves the caller's own cart internally, so posting to another customer's path succeeds (against the caller's own cart) instead of 403.
2. `CheckoutSecurityApiTest.preventsCustomerFromCheckingOutAnotherCustomersCart:82` — expected 403, got 201. Same path-param-ignored root cause on the checkout route.
3. `OperationsReassignmentTest.opsCanReassignAndNonOpsCannot:45` — expected 200, got 403. Root cause: test authenticates `ROLE_OPERATIONS` but `OperationsDispatchController.java:21` gates `hasAnyRole('OPS','ADMIN')` — this is exactly audit finding SEC-21 (OPS vs OPERATIONS split); **approved Batch 9 would have fixed it**.
4. `AdminAuthorizationTest.rejectsNonAdminRoles` — ApplicationContext load error: `AdminController` gained a constructor dependency on `AppUserProfileService` and the `@WebMvcTest` slice provides no mock for it.

Per the execution protocol ("gate red BEFORE the batch's changes → record and hold the lane's batches; do not fix unrelated pre-existing failures"), all 13 BE batches were skipped. Fixing failures 1, 2, 4 is outside every approved batch scope; fixing them autonomously would violate the protocol.

**To unblock:** fix the 4 baseline failures (items 1–2 are a real API-contract/authz gap worth fixing on their own; item 3 is Batch 9's one-line standardization; item 4 is a missing `@MockBean`), then re-run Lane BE against PLAN.md — all 13 batch specs remain valid, and Batch 1 is fully scoped (findings re-verified as still present by the Batch 1 agent).

### Lane BO held in full — `npm run validate` red on the pristine tree

`npm run lint` (`eslint . --max-warnings=0`) fails with **7 errors + 23 warnings**, all pre-existing and outside every planned batch's file scope. The 7 errors: `scripts/extract-strings.js:1,2` (`no-require-imports`), `src/app/register/page.tsx:99,107` and `src/lib/api/types.ts:311` (`no-explicit-any`), `src/components/ui/label.tsx:6` + `src/components/ui/textarea.tsx:6` (`no-empty-object-type`). `typecheck` and `validate:screens` are clean. This matches the risk PLAN.md's Batch 12 predicted ("report rather than loosen the gate").

All seven BO findings (COR-10, SEC-14, SEC-15, SEC-17, COR-11, COR-13, SEC-21-logout) were re-verified as still present before the lane stopped; no files were edited.

**To unblock:** fix the 7 lint errors (and ideally the 23 warnings), then re-run Lane BO (Batches 14 → 15-BO → 18-BO) — specs remain valid. Note: the new CI backoffice job (`75aae6f`) runs this same `npm run validate`, so that job will be red until this is fixed.

## Pending manual verification

- **CI (Batch 12):** first live workflow run (branch not pushed — pushing is out of scope). Known risk: backoffice lint `--max-warnings=0` may fail on existing warnings at first run.
- **DW:** expire/tamper token → confirm 401 → login redirect; backend down → cart error banners appear.
- **DW / SEC-21 residual:** ROPC sessions cannot invoke Keycloak end-session (no `id_token` stored). Local artifacts are cleared consistently; true SSO termination needs `scope=openid` id_token storage or the (skipped) realm-hardening item.
- **DW hygiene note:** `pede-aqui-delivery/tsconfig.tsbuildinfo` is an untracked `tsc -b` artifact — consider gitignoring (left alone per no-deletion constraint).
- **FL:** emulator smoke of login/registration against the local stack; run `pod install` once for iOS (new Podfile from the pub add); confirm `flutter build apk --release` without dart-defines aborts via the new mock guard.
- **FL / backend gap:** `/couriers/me/earnings-summary` has no per-period fields — earnings tiles legitimately show 0 until the backend adds them; earnings *history* still needs a real endpoint (documented in code comments).

## Additional findings surfaced during execution

1. **`.gitignore` was silently excluding Flutter SOURCE dirs:** root patterns `data/` (line 32) and `screens/` (line 50) match `lib/**/data/**` and `lib/presentation/screens/**` — several Flutter source files were never tracked by git. The FL lane force-added exactly the 8 such files it edited (visible as `create mode` in `41531ec`); **other untracked source files in those dirs likely remain — anchor those two gitignore patterns (e.g. `/data/`, `/screens/`) and audit `git status --ignored` in both Flutter apps.**
2. **Baseline cart/checkout path-param authz gap** (found via the failing baseline tests, see Lane BE hold): `POST /customers/{customerId}/cart/*` and checkout ignore the `{customerId}` path segment entirely. Not exploitable for cross-customer writes (service resolves the caller's own cart) but the API contract and its security tests say 403 — fix server-side when unblocking Lane BE.
3. **`pede-aqui-backoffice/tsconfig.tsbuildinfo` is a tracked build artifact** — it churns on every typecheck (restored to committed state by the orchestrator); should be gitignored + untracked in a hygiene pass.

## Intentionally skipped sign-off items (by instruction)
Keycloak realm hardening (SEC-08); any Flyway migration (V028+, incl. potential user→vendor linkage); `quantityReserved` data correction; everything in PLAN.md's "Deferred" section; PROD-11 repo hygiene deletions.

## Still-open user actions (restated from PLAN.md — NOT done here)
1. Rotate the AWS IAM key pair referenced by `.env:11-12` and the key in `delivery-springboot-dev_accessKeys.csv`; move both files out of the working tree.
2. Rotate the Keycloak admin client secret and DB password wherever the committed defaults were used.
3. Keycloak realm hardening (SEC-08) — skipped by instruction, remains open.

## How to review

```bash
git log --oneline main..remediation/plan-2026-07-12       # all commits (6: 1 docs + 5 batches)
git diff main..remediation/plan-2026-07-12 --stat          # full footprint
git show 45058db   # batch-15-DW  (delivery web: 401, cart errors, redirect, logout)
git show 86cb662   # batch-18-DW  (delivery web: vitest harness, 11 tests)
git show 75aae6f   # batch-12     (CI: 5 real jobs)
git show 83eaf43   # batch-12b    (CI: delivery-web npm test)
git show 41531ec   # batch-16     (Flutter: largest diff — 31 files, both apps)
# Re-run gates locally:
cd pede-aqui-delivery && npm run typecheck && npm test && npm run build
cd pede_aqui_delivery_app && flutter analyze && flutter test
cd pede_aqui_courier_app && flutter analyze && flutter test
```

## Honest assessment — what a human must review before merge, ranked by risk

The two highest-risk planned batches (8 — tenant header hardening, 10 — prod fail-fast) **did not land**: the entire backend lane was held by a red baseline gate, so this branch contains **no backend changes at all** — none of the CRITICAL authorization fixes (SEC-01…05, COR-01) are in it. Review priority for what DID land: (1) **batch-16** — the largest and most behavioral diff: secure-storage session restore and the cart customer-id provider change how both mobile apps authenticate; the force-added files from the `.gitignore` bug deserve a careful look, and the release mock-guard must be confirmed with a real `--release` build; (2) **batch-15-DW** — the 401 interceptor does a hard `window.location.assign` redirect and the new shared logout triggers a Keycloak `signoutRedirect` when an id_token exists — verify both against real session flows; (3) **batch-12/12b** — the backoffice CI job will be **red on first run** because of the 7 pre-existing lint errors (documented above); decide whether to fix those first or land CI red as a forcing function; (4) **batch-18-DW** is low-risk (dev-deps + tests only). The fastest path to the audit's real payoff is: fix the 4 backend baseline test failures + 7 backoffice lint errors, then re-run Lanes BE and BO against the unchanged PLAN.md.
