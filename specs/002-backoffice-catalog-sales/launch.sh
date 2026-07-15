#!/usr/bin/env bash
# Launch the Spec-002 orchestrated implementation on Claude Fable 5.
# Run from the repo root AFTER copying .claude/ into the repo and confirming the spec is at
# specs/002-backoffice-catalog-sales/. Requires local infra up (dev profile / MinIO):
#   docker compose up -d postgres redis keycloak minio minio-init
set -euo pipefail

MODEL="claude-fable-5"

# 1) Sanity: start on a feature branch (orchestrator will create it too, but be explicit).
git rev-parse --abbrev-ref HEAD

# 2a) INTERACTIVE (recommended for a first run — you can watch each wave and approve PRs):
#     Launch on Fable 5, verify agents loaded with /agents and model with /model, then paste
#     the contents of PROMPT.md.
claude --model "$MODEL"

# 2b) HEADLESS (unattended pipeline). Uncomment to run non-interactively. Permissions come from
#     .claude/settings.json (deny > ask > allow); 'ask' items will block in headless mode, which
#     is intended for push/merge/PR. Do NOT add --dangerously-skip-permissions.
# claude -p "$(cat PROMPT.md)" \
#   --model "$MODEL" \
#   --permission-mode acceptEdits \
#   --append-system-prompt "Follow .claude/agents/* model routing exactly. Halt on any guardrail hit."

# Notes:
# - Complex lanes inherit Fable 5 because the session model is Fable 5 and their agent files set
#   model: inherit. The frontend agent pins model: sonnet. Confirm with /model + /agents.
# - If your Claude Code version accepts explicit model strings in agent frontmatter, you may
#   replace `model: inherit` with `model: claude-fable-5` for stricter pinning.
