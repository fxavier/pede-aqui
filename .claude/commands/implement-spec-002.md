# Orchestration prompt — Implement Spec 002 (Backoffice Catalog, Sales & Reports)

You are the **orchestrator** for implementing `specs/002-backoffice-catalog-sales/`. You run
on **Claude Fable 5**. You own all fan-out: subagents cannot spawn subagents, so every
parallel dispatch comes from you. Delegate work to the `spec-002-*` subagents defined in
`.claude/agents/`. Do not implement lane work yourself in the main thread — dispatch it.

## 0. Ground rules (read before doing anything)

1. **Read the spec first**, fully, before dispatching: `specs/002-backoffice-catalog-sales/`
   → `spec.md`, `plan.md`, `data-model.md`, `contracts/openapi.yaml`, `tasks.md`.
2. **Branch, don't touch main.** Create and work on `feat/spec-002-backoffice-catalog-sales`.
   Commit after each subagent completes and its gate passes. Do **not** push, merge to main,
   force-push, or reset --hard. Opening a PR requires human approval (ask).
3. **Guardrails are hard.** Never run: prod Spring profile (`SPRING_PROFILES_ACTIVE=prod`),
   `terraform *`, any `aws *`, `rm -rf`, `DROP`/`TRUNCATE` SQL, or read secret files
   (`.env*`, `*.pem`, `~/.aws/**`). The AWS access-key rotation is an unresolved manual
   action; the prod image-upload path stays untested here. Dev profile + MinIO only.
4. **Respect file ownership** (below) to keep parallel lanes conflict-free. If a subagent
   reports it must edit a file owned by another lane, stop that lane and resolve serially.
5. **Every schema change is a Flyway migration** (`ddl-auto: validate`); no Hibernate DDL.
   Preserve the simple layered architecture — no DDD/CQRS/Kafka.
6. **Gates are non-negotiable.** A lane is "done" only when its `[test]` tasks pass and the
   relevant build is green (`cd backend && mvn clean verify` / `cd pede-aqui-backoffice &&
   npm run validate`). Do not proceed to the next wave until the current wave's gates pass.

## 1. Model routing (already enforced by agent files — confirm, don't override)

| Agent | Wave | Model | Why |
|---|---|---|---|
| orchestrator (you) | — | Fable 5 | whole-system reasoning, dependency control |
| `spec-002-core` | 0 | Fable 5 (`inherit`) | shared entity/tx/formula changes |
| `spec-002-catalog` | 1 | Fable 5 (`inherit`) | price-review + moderation logic |
| `spec-002-sales` | 1 | Fable 5 (`inherit`) | state-machine guards, idempotent refund, isolation |
| `spec-002-promotions` | 1 | Fable 5 (`inherit`) | transactional discount + concurrency |
| `spec-002-reports` | 1 | Fable 5 (`inherit`) | aggregation correctness |
| `spec-002-frontend` | 2 | Sonnet | screen/service wiring against a fixed contract |
| `spec-002-integrator` | 3 | Fable 5 (`inherit`) | cross-system smoke/regression reasoning |

If any complex agent reports it is not on Fable 5, halt and fix the agent file / session
model before continuing. Run `/agents` and `/model` to verify at start.

## 2. Wave plan (dependency-ordered; dispatch each wave's agents in parallel)

### Wave 0 — critical path, serial (`spec-002-core`)
Do **all** shared/cross-cutting changes here so downstream lanes are disjoint:
- Lane A migrations A1–A6 (and A7 category snapshot **only if** the human confirmed adoption;
  otherwise skip A7 and leave by-category on live-join).
- Shared entity edits: `Order` (+`discountTotal`,+`appliedPromotionId`), `OrderItem`
  (+`categoryIdSnapshot` if A7), `Cart`/`CartItem` (+discount fields).
- Centralise the pricing formula `total = subtotal + fees + taxes − discount_total` and expose
  the checkout discount hook point (a method/seam `spec-002-promotions` will fill in), leaving
  `discount_total` defaulting to 0 so legacy behaviour is unchanged.
- Gate: `mvn clean verify` green (migrations apply on a fresh DB, `validate` passes). Commit.

### Wave 1 — parallel, after Wave 0 merges (4 agents at once)
Dispatch **concurrently**: `spec-002-catalog`, `spec-002-sales`, `spec-002-promotions`,
`spec-002-reports`. Each implements its lane end-to-end **including its own `[test]` tasks**,
and confirms `mvn clean verify` green for its scope before reporting done. Commit each on
completion. Wave 1 is complete only when all four are green.
- `spec-002-catalog` → Lane B (catalog package + moderation + config props).
- `spec-002-sales` → Lane C (new `sales` package; reuse order state machine + finance/refund +
  notification; **do not edit** `CheckoutService`).
- `spec-002-promotions` → Lane D (`marketing` promotions + resolver + cart coupon + fill the
  checkout discount seam from Wave 0). Owns the checkout discount logic.
- `spec-002-reports` → Lane E (new `report` package; read-only over orders; CSV streaming).

### Wave 2 — frontend, after Wave 1 (staged)
- **2a (serial):** one `spec-002-frontend` invocation for Lane F1 only — `types.ts` +
  `services.ts` clients aligned to `contracts/openapi.yaml`. Gate: `npm run validate`.
- **2b (parallel):** four `spec-002-frontend` invocations, one per route, concurrently:
  `/catalogo` edit+moderation (F2), `/sales` (F3), `/marketing` promotions (F4), `/reports`
  (F5). Each includes RBAC gating and loading/empty/error/forbidden states (F6). Gate per
  invocation: `npm run validate` + `npm run build`.

### Wave 3 — integrate & harden, serial (`spec-002-integrator`)
Lane G: audit-action coverage check, `spec-002-smoke.sh`, docs (`Usage.md`, `CLAUDE.md` route
table), full `mvn clean verify` + backoffice `npm run validate && npm run build`, and the
regression checks (checkout single-transaction + idempotency intact; totals reconcile with
discount; report figures read snapshots not live price). Commit. Then summarise and **ask**
before opening a PR.

## 3. File-ownership map (prevents parallel conflicts)

| Owner | Owns (writes) |
|---|---|
| `spec-002-core` | `db/migration/*`, `Order`/`OrderItem`/`Cart`/`CartItem` entities, pricing-formula util, checkout discount seam |
| `spec-002-catalog` | `catalog/**` (Product/Sku entity price+image fields, services, controllers, DTOs), catalog config props |
| `spec-002-sales` | `sales/**` (new). Read-only reuse of `order`/`payment`/`finance`/`notification` services (call, don't edit) |
| `spec-002-promotions` | `marketing/**`, `cart/**` coupon endpoints, and the checkout discount logic behind the Wave-0 seam |
| `spec-002-reports` | `report/**` (new). Read-only over `orders`/`order_item`/`refund`/`commission` |
| `spec-002-frontend` | `pede-aqui-backoffice/src/**` |
| `spec-002-integrator` | `scripts/**`, `Usage.md`, `CLAUDE.md`, top-level verify |

Shared entities are edited **only** in Wave 0. If a Wave-1 lane needs a shared-entity change
that Wave 0 missed, pause parallelism, make the change in a short serial `spec-002-core`
follow-up, re-verify, then resume.

## 4. Reporting protocol

After each wave: post a concise status — agents dispatched, files changed, gate results,
and any deviations from spec (with the spec §/AC reference). Surface open decisions from
`spec.md §7` / `plan.md §4.2` if a lane hits them (category snapshot, timestamp basis,
threshold value) and **ask** rather than guessing. Keep going until Wave 3 is green; then stop
and request PR approval.

Begin with §0.1 (read the spec) and §1 verification (`/agents`, `/model`), then Wave 0.
