# Spec 002 — Claude Code parallel implementation kit

Runs the `specs/002-backoffice-catalog-sales/` build as an orchestrated, multi-agent Claude
Code pipeline. The orchestrator and all complex lanes run on **Claude Fable 5**; the frontend
lane runs on **Sonnet**.

## Layout & placement

Copy into your repo root:

```
.claude/
  settings.json                 # session model = claude-fable-5 + pre-authorized permissions
  agents/
    spec-002-core.md            # Wave 0  · Fable 5 (inherit) · migrations + shared entities/tx
    spec-002-catalog.md         # Wave 1  · Fable 5 (inherit) · Lane B
    spec-002-sales.md           # Wave 1  · Fable 5 (inherit) · Lane C
    spec-002-promotions.md      # Wave 1  · Fable 5 (inherit) · Lane D (owns checkout discount)
    spec-002-reports.md         # Wave 1  · Fable 5 (inherit) · Lane E
    spec-002-frontend.md        # Wave 2  · Sonnet           · Lane F (services + per-route)
    spec-002-integrator.md      # Wave 3  · Fable 5 (inherit) · Lane G
PROMPT.md                       # paste into the Fable 5 session to start the orchestrator
launch.sh                       # convenience launcher
```

`specs/002-backoffice-catalog-sales/` (the five spec files) must also be in the repo — the
agents read them directly.

## How model routing is enforced

Prompt text can't pick a subagent's model — the agent definition file does. The session is
launched on Fable 5 (`--model claude-fable-5`, also in `settings.json`); every complex agent
sets `model: inherit`, so it runs on the session's Fable 5. The frontend agent sets
`model: sonnet`. Verify at start with `/model` and `/agents`. If your Claude Code version
supports explicit model strings in frontmatter, swap `inherit` → `claude-fable-5` for hard pins.

## Wave / dependency plan

```
Wave 0  (serial)     spec-002-core          migrations + Order/OrderItem/Cart discount fields,
                                             pricing formula, checkout discount seam
                         │  (gate: mvn clean verify)
Wave 1  (4 parallel)  ┌── spec-002-catalog     Lane B
                      ├── spec-002-sales        Lane C
                      ├── spec-002-promotions   Lane D  (fills checkout seam)
                      └── spec-002-reports      Lane E
                         │  (gate: each mvn clean verify green)
Wave 2a (serial)      spec-002-frontend [services]   types.ts + services.ts to OpenAPI
Wave 2b (4 parallel)  spec-002-frontend [catalog|sales|promotions|reports]
                         │  (gate: npm run validate + build)
Wave 3  (serial)      spec-002-integrator     audit/smoke/docs/regression/full verify
                         │  → orchestrator summarises, ASKS before PR
```

Conflict-free parallelism comes from two rules the orchestrator enforces: (1) all shared-entity
edits happen only in Wave 0; (2) each Wave-1 lane owns a disjoint package (`catalog` / `sales`
/ `marketing`+`cart`+checkout-discount / `report`). Sales reuses order/finance/notification by
calling, never editing.

## Run

```bash
# from repo root, infra up (dev profile / MinIO):
docker compose up -d postgres redis keycloak minio minio-init
bash launch.sh          # opens Claude Code on Fable 5; then /agents, /model, paste PROMPT.md
```

## Guardrails (in settings.json + PROMPT.md)

- Feature branch only; no push/merge/PR without human approval (`ask`).
- Hard-denied: `rm -rf`, force-push, `reset --hard`, `terraform`, `aws`, prod Spring profile,
  reading `.env*`/`*.pem`/`~/.aws`. Dev profile + MinIO only.
- The AWS access-key rotation is unresolved, so the **prod image-upload path is not exercised**.
  Close the rotation before any prod deploy that touches image upload.

## Verify vs. your Claude Code version

Two version-sensitive details to confirm once: the permission matcher form
(`Bash(mvn:*)` vs `Bash(mvn *)`) in `settings.json`, and whether agent frontmatter accepts an
explicit `model: claude-fable-5`. Everything else is stable across recent versions.
