# Project Audit Report
**Date:** 2026-06-06  
**Scope:** `/pede-aqui` root workspace

---

## Files Moved

| From | To | Reason |
|---|---|---|
| `translacoes-pt-pt.txt` | `docs/translacoes-pt-pt.txt` | Translation file belongs with project docs |
| `docs/new/Backend__Document_upload_for_PDFs_*.md` | `docs/` | Loose "new" subfolder merged into parent |
| `docs/new/Backend__Flyway_V012_*.md` | `docs/` | Same |
| `docs/new/Rich_Profiles_&_Document_Attachment_*.md` | `docs/` | Same |
| `specs/new/Inventário_de_Ecrãs_*.md` | `specs/` | Loose "new" subfolder merged into parent |
| `specs/new/Logicore_Backoffice_*.md` | `specs/` | Same |
| `specs/new/Pede_Aqui_—_Alinhamento_*.md` | `specs/` | Same |
| `specs/new/Spec__App_Cliente_*.md` | `specs/` | Same |
| `specs/new/Spec__App_Estafeta_*.md` | `specs/` | Same |
| `specs/new/[FASE_1]_*.md` | `specs/` | Same |
| `specs/new/[FASE_2]_*_checkout.md` | `specs/` | Same |
| `specs/new/[FASE_2]_*_polling.md` | `specs/` | Same |

The empty `docs/new/` and `specs/new/` folders can now be deleted.

---

## 🔴 Security — Needs Immediate Attention

### `delivery-springboot-dev_accessKeys.csv`
A CSV file containing what appear to be cloud service access keys is sitting at the project root. It is excluded from git by `.gitignore` (`*.csv` rule), so it has not been committed, but it should not live inside the project directory at all. **Move it out of the repo folder and revoke/rotate the credentials if there is any chance they were ever exposed.**

---

## 🟠 Duplicates — Manual Review Required

### `prometheus.yml` (root vs `backend/prometheus.yml`)
These two files are **identical**. One should be removed. The root copy is likely the authoritative one since the root `docker-compose.yml` is more complete. Recommended action: delete `backend/prometheus.yml`.

### `docs/` (root) vs `backend/docs/`
The following files are **byte-for-byte identical** across both locations:
- `api.md`
- `architecture.md`
- `local-development.md`
- `Usage.md`

These are almost certainly the same docs that ended up duplicated when `backend/` was added as a submodule. The root `docs/` folder is the canonical location. The copies inside `backend/docs/` can be removed once you decide whether `backend/` will continue as a submodule or be merged.

### `web/` vs `backend/web/`
Both are Next.js apps with `package.json` name `delivery-marketplace-web`. The files differ — `backend/web/` appears to be an older or diverged copy. Determine which is the live version and remove the stale one. The root `web/` is likely current; `backend/web/` may be an artifact from when the project was restructured.

### `backend/mobile/courier_app` & `backend/mobile/delivery_app` vs `pede_aqui_courier_app/` & `pede_aqui_delivery_app/`
The root-level Flutter apps (`pede_aqui_courier_app/`, `pede_aqui_delivery_app/`) are more complete (contain `android/`, `ios/`, `assets/`, platform configs). The `backend/mobile/` copies are stripped down (only `lib/`, `build/`, `test/`). The `backend/mobile/` versions look like outdated shells. Recommend removing `backend/mobile/` once confirmed the root apps are authoritative.

### `backend/src/` (zero Java files) vs `backend/backend/src/`
`backend/src/` exists but contains no `.java` files. All actual source code lives in `backend/backend/src/`. The `backend/src/` tree appears to be an empty scaffold left behind from a restructure. Safe to delete after confirming nothing references it.

### `backend/specs/` vs root `specs/`
`backend/specs/` contains `001-delivery-marketplace-mvp/` and a `new/` folder mirroring the root structure. Confirm whether these are in sync; the root `specs/` is the canonical location.

---

## 🟡 Structural Issues — Worth Discussing

### `backend/` is a nested Git repository (submodule)
`backend/.git/config` points to `https://github.com/fxavier/pede-aqui.git` — the **same remote URL as the root repo**. This means the root and `backend/` are both tracking the same remote, which is almost certainly unintentional. This recursive submodule structure will cause confusing `git` behavior (duplicate histories, push/pull conflicts). The `backend/` folder likely started as a copy of the repo before a monorepo refactor. Recommended: remove `backend/` as a submodule and keep only the contents that don't already exist at the root.

### `~/` folder accidentally created at root
There is a folder literally named `~` in the project root (i.e., `pede-aqui/~/`). This is created when someone runs `mkdir ~/some-path` or a tool expands `~` incorrectly. It contains traycer AI agent artifacts (`~/.traycer/yolo_artifacts/`). These JSON artifacts are agent session data and can safely be deleted once no longer needed. The `~/` folder itself should be removed.

### `backend/backend/` nested naming
The actual Spring Boot source lives at `backend/backend/` (i.e., two levels of `backend`). The inner `backend/backend/pom.xml` is the real Maven root. This naming arose because the `backend/` submodule itself has a `backend/` subdirectory. If the submodule is dissolved, rename `backend/backend/` to just `backend/` at the root.

---

## 🟡 Screens — Mismatched Filenames

Several screen design folders contain files named after a **different** screen. This suggests files were copy-pasted into wrong folders:

| Folder | Files inside (wrong name) |
|---|---|
| `screens/pede-aqui-backoffice/edit_product_vendor_portal/` | `customer_segments_marketing_portal.html/.png` |
| `screens/pede-aqui-backoffice/menu_management_vendor_portal/` | `marketing_analytics_roi_reports.html/.png` |
| `screens/pede-aqui-backoffice/promotion_management_vendor_portal/` | `product_catalog_vendor_portal.html/.png` |

Either the files need to be renamed to match their folder, or the files belong in a different folder. Manual review required.

---

## 🟢 Clean — No Action Needed

| Item | Status |
|---|---|
| `keycloak/delivery-realm.json` | Correctly placed infrastructure config |
| `docker-compose.yml` (root) | More complete than `backend/` copy; appears authoritative |
| `pede-aqui-backoffice/` | Correctly placed, separate Next.js admin app |
| `screens/` | Excluded from git via `.gitignore`; fine as design asset store |
| `.opencode/`, `.specify/`, `.traycer/` (root) | AI tooling directories; expected |
| `.github/workflows/ci.yml` | Standard CI config |
| `AGENTS.md`, `README.md` | Root-level docs in correct location |
| `specs/001-delivery-marketplace-mvp/` | Well-structured spec directory |

---

## Suggested Next Steps (Priority Order)

1. **Rotate/revoke** the credentials in `delivery-springboot-dev_accessKeys.csv` and move the file outside the project.
2. **Resolve the `backend/` submodule** — it points to the same remote as root. Decide: keep as submodule, merge, or remove.
3. **Delete `~/` folder** (accidental creation; only contains traycer session artifacts).
4. **Remove `prometheus.yml`** from `backend/` (identical to root copy).
5. **Remove identical doc copies** from `backend/docs/`.
6. **Resolve `web/` vs `backend/web/`** naming conflict and remove the stale copy.
7. **Fix misnamed screen files** in the three affected folders.
8. **Delete empty** `docs/new/` and `specs/new/` folders.
