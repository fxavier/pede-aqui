# RUNBOOK — executing the Pede Aqui provisioning specs in Claude Code

How to run the subscription → tenant/user provisioning work (and the surrounding subscriptions-platform
improvements) with Claude Code using parallel subagents and the proper skills.

**Specs live in:**
- Backend (OpenSpec change): `pede-aqui/openspec/changes/subscription-provisioning/`
- Subscriptions platform: `pede-aqui-subscriptions-platform/specs/provisioning/` (README + `00`–`06`)

The flagship spans BOTH repos. Hard ordering: **Wave 0 (backend) freezes the HTTP contract → Wave 1
(subscriptions) codes against it.** Everything else within Wave 1 is parallelizable by ownership.

---

## 0. Setup

The flagship is cross-repo, so open one session rooted at the parent with both dirs added:

```bash
cd /Users/xavier/dev/code/workspace/2026
claude --add-dir pede-aqui --add-dir pede-aqui-subscriptions-platform
```

(Or run two separate sessions, one per repo — do the backend one first.)

Verify discovery inside the session:

```
/agents     # backend-prisma, subscribe-flow, … (subscriptions) should appear
/skills     # nextjs16-prisma7-marketing, openspec-apply-change, engineering:*, …
```

Practical settings:
- **Permissions:** press `Shift+Tab` to toggle *accept edits* for long autonomous runs (preferred over
  `--dangerously-skip-permissions`).
- **Model:** Opus for design/review (Wave 0, integrator-qa); Sonnet is enough for mechanical agents
  (05 deps/types, tests). Set per-agent if desired.
- **Isolation (optional):** for parallel agents editing the same tree, use git worktrees
  (`--isolation worktree` per agent, or one worktree per wave). Not required when agents own disjoint files.

---

## 1. Wave 0 — backend endpoint (run in / against `pede-aqui`)

Paste:

> Read `openspec/changes/subscription-provisioning/` (`proposal.md`, `design.md`, `tasks.md`,
> `specs/tenant-provisioning/spec.md`). Implement this change using the `openspec-apply-change` skill.
> Start with **task 1**: open the real `MerchantRegistrationController`, `MerchantRegistrationRequest`,
> `MerchantRegistrationResponse`, `MerchantRegistrationService`, `KeycloakAdminService`, and
> `SecurityConfig`, confirm the contract, and reconcile any drift into the spec BEFORE coding. Then
> implement tasks 2–7. Load `engineering:system-design` for the endpoint design and
> `engineering:testing-strategy` for the tests. Run `./mvnw verify` and keep
> `MerchantRegistrationServiceTest` green.

When the endpoint is implemented and the contract is stable, **freeze it** and archive the change with
the `openspec-archive-change` skill (or the repo's `/opsx` command).

**Exit criteria:** `POST /api/v1/provisioning/tenants` exists, M2M-secured, idempotent; `./mvnw verify`
passes; OpenAPI shows the endpoint; the request/response shape matches
`specs/tenant-provisioning/spec.md`.

---

## 2. Wave 1 — subscriptions platform, parallel (run in / against `pede-aqui-subscriptions-platform`)

Paste:

> Read `specs/provisioning/00-ORCHESTRATION.md` and specs `02`–`05`. Implement **Wave 1 with parallel
> subagents** (Task tool), **one per spec file**, respecting each file's *Ownership boundary* so they
> don't conflict. Assign `02` and the `subscribe.ts` part of `05` to the **same** agent. Each agent
> loads the `nextjs16-prisma7-marketing` skill and follows `CLAUDE.md`. Code `02` against the frozen
> backend contract in
> `../pede-aqui/openspec/changes/subscription-provisioning/specs/tenant-provisioning/spec.md`.
> After the agents finish, do an **integrator-qa** pass: resolve `INTEGRATION_NOTES.md`, run
> `npm run typecheck && npm run lint && npm run build && npm run test`, and apply
> `engineering:code-review` over the diff.

Suggested concurrent dispatch (disjoint ownership):
- Agent A → `02` provisioning integration **+** the `subscribe.ts` slice of `05`
  (`src/lib/provisioning/**`, env, one call-site edit)
- Agent B → `03` lifecycle + payments (`src/app/actions/lifecycle.ts`, `src/lib/payments/**`, webhook)
- Agent C → `05` hardening minus the `subscribe.ts` slice (deps, type fix, rate-limit, logging, vitest harness)
- Agent D → `04` admin console (`src/app/(admin)/**`) — start after `02`/`03` field signatures are frozen

**Env to set before running the provisioning path** (server-only, `.env`): `BACKEND_BASE_URL`,
`KEYCLOAK_TOKEN_URL`, `PROVISIONER_CLIENT_ID`, `PROVISIONER_CLIENT_SECRET`, `PROVISION_ON=subscribe`.
Do a clean install first (spec `05`): `@prisma/client` must be added to `package.json`.

---

## 3. Wave 2 — verification (single agent, after Wave 1 merges)

Paste:

> Run an end-to-end verification of the provisioning flow. With the backend running and the
> `subscriptions-provisioner` Keycloak client configured, exercise `/subscribe` and assert the
> `Subscription` row gains `provisioningStatus=PROVISIONED` + `tenantId/tenantSlug/ownerUserId`, and
> that a duplicate submit does not create a second tenant (idempotency). Then apply
> `engineering:code-review` over the full diff of both repos and list any correctness/security gaps.

---

## 4. Order & dependency cheat-sheet

```
01 backend (Wave 0)  ── freeze contract ──►  02 integration ─┐
                                             03 lifecycle ────┤ (parallel)
                                             05 hardening ────┘
                                                   └──► 04 admin (needs 02/03 fields)
                                                              └──► Wave 2 integrator-qa + E2E
```

- `02` and the `subscribe.ts` part of `05` → **same agent** (same file).
- `04` depends on the `Subscription` columns added by `02` (provisioning) and `03` (billing) — give it
  their frozen signatures if run concurrently.
- Backlog (`06`) is scout-then-promote: spawn an `Explore` agent per item, then write a `01`-style spec.

## 5. Notes

- Skill→spec map and full ownership boundaries: `specs/provisioning/00-ORCHESTRATION.md`.
- Backend contract was reconstructed from tests/migration/`application.yml`; task 1 of the OpenSpec
  `tasks.md` confirms it against the real `main/java` sources before coding.
