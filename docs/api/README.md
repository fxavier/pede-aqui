# API contract (OpenAPI)

`docs/api/openapi.json` is the versioned HTTP contract of the backend (LAC-56). It is generated from
the code, committed, and checked by CI on every PR. Clients (backoffice, customer web, Flutter apps)
should treat it as the source of truth for request/response shapes.

| File | What it is |
|---|---|
| `openapi.json` | Current contract. Generated, never edited by hand. |
| `openapi-baseline.json` | Historical snapshot taken on `54be398` (INC-00, see `docs/testing/BASELINE.md`). Kept for reference only; CI does not use it. |
| `API-MIGRATION.md` | Human-written notes for every contract change that affects clients. |
| `ERROR-CONTRACT.md` | Error payload and error codes. |

## How the spec is generated

`backend/src/test/java/com/delivery/common/OpenApiContractExportTest.java` boots the full Spring
context (the same one the other `PostgresApiE2ETestBase` tests use: Testcontainers Postgres, Keycloak
mocked at its two seams; no Keycloak, Redis or MinIO needed), calls `GET /api-docs` through MockMvc and
writes the result to `backend/target/openapi.json` in a deterministic form:

- object keys sorted, 2-space indentation, LF line endings, trailing newline
  (byte-identical to `json.dumps(spec, sort_keys=True, indent=2)`);
- the `servers` block removed (it only contains the generating host/port).

The test runs as part of `mvn verify`. It needs Docker (or a `SPRING_DATASOURCE_URL` pointing at a
Postgres); without either it is skipped and no file is written.

## Updating the contract after an API change

```bash
cd backend
mvn test -Dtest=OpenApiContractExportTest -Dopenapi.output=../docs/api/openapi.json
git diff ../docs/api/openapi.json   # review, then commit it with the code change
```

(Equivalent: run the test without `-Dopenapi.output` and copy `target/openapi.json` over
`docs/api/openapi.json`.)

## What CI checks

1. **Freshness** (`ci.yml`, backend job, after `mvn clean verify`): `docs/api/openapi.json` must be
   byte-identical to the freshly generated `backend/target/openapi.json`. A code change that alters the
   API without regenerating the file fails here; the diff is printed in the log and the generated file
   is uploaded as the `openapi` workflow artifact. A missing generated file (test skipped) also fails.
2. **Breaking changes** (`api-contract.yml`, PRs only): runs
   [oasdiff](https://github.com/oasdiff/oasdiff) `breaking` (pinned image
   `tufin/oasdiff:v1.11.7` by digest) between the base commit's `docs/api/openapi.json` and the PR's.
   Any `ERR`-level change fails the job (warnings are reported but do not fail). The job summary also
   shows the full `oasdiff changelog`. If the base branch has no `docs/api/openapi.json` yet, the check
   is skipped with a notice.

Because (1) guarantees the committed file matches the code, (2) only needs the committed files and does
not rebuild the backend.

## Shipping an intentional breaking change

1. Prefer an additive change (new field, new endpoint, deprecate the old one first).
2. If the break is intended, add the **`api-breaking`** label to the PR. The workflow re-runs on
   `labeled`/`unlabeled`; with the label, breaking changes are still listed (as annotations and in the
   summary) but the job passes.
3. Document the change in `API-MIGRATION.md` (what changed, who is affected, what clients must do)
   and update the affected clients in the same PR or a linked one.

The label must exist in the repository (create it once: `gh label create api-breaking --color B60205
--description "PR intentionally breaks the HTTP API contract"`).

## Running oasdiff locally

```bash
git show origin/main:docs/api/openapi.json > /tmp/openapi-base.json
docker run --rm -v /tmp:/base:ro -v "$PWD/docs/api:/rev:ro" tufin/oasdiff:v1.11.7 \
  breaking /base/openapi-base.json /rev/openapi.json --fail-on ERR
```

## Notes

- The contract is OpenAPI 3.0.1 (`springdoc.api-docs.version: openapi_3_0` in `application.yml`).
- Generated TypeScript/Dart clients from this file are planned for a later wave; not in place yet.
- Comparing `openapi-baseline.json` (54be398) with the first committed `openapi.json` shows 80
  ERR-level differences, almost all bean-validation constraints now surfaced in the spec (`minLength: 1`
  from `@NotBlank`, `format: email`, required fields) plus earlier documented changes (opening-hours
  `LocalTime` as `string/time-local`, `deliveryFee` as decimal, presigned-url `contentLength`,
  required delivery `confirmationCode`, `POST /checkout` no longer documenting `201`). Those
  predate this gate; `openapi.json` is the new reference from here on.
