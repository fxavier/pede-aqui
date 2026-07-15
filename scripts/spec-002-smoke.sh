#!/usr/bin/env bash

# Spec-002 smoke: asserts the backoffice catalog/sales/promotions/reports endpoints are present
# in the live OpenAPI and runs a happy path per feature against the local dev stack
# (docker compose postgres/keycloak/minio + `mvn spring-boot:run`, dev profile only).
#
# Env overrides: BASE_URL, KEYCLOAK_URL, SMOKE_USER, SMOKE_PASS, SMOKE_TENANT_ID.
# Set SPEC002_SMOKE_OPENAPI_ONLY=1 to skip the authenticated happy-path section.

set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
KEYCLOAK_URL="${KEYCLOAK_URL:-http://localhost:8081}"
KEYCLOAK_REALM="${KEYCLOAK_REALM:-delivery}"
KEYCLOAK_CLIENT_ID="${KEYCLOAK_CLIENT_ID:-delivery-app}"
# Seeded dev realm platform admin (keycloak/delivery-realm.json); never use against prod.
SMOKE_USER="${SMOKE_USER:-admin}"
SMOKE_PASS="${SMOKE_PASS:-admin123}"

fail() { echo "[spec-002-smoke] FAIL: $*" >&2; exit 1; }
note() { echo "[spec-002-smoke] $*"; }

note "Checking backend health"
curl -sf "${BASE_URL}/actuator/health" >/dev/null || fail "backend health check (${BASE_URL}/actuator/health)"

note "Verifying Spec-002 paths in OpenAPI"
DOCS_JSON="$(curl -sf "${BASE_URL}/api-docs")" || fail "OpenAPI endpoint (${BASE_URL}/api-docs)"
for path in \
  "/api/v1/catalog/products/{productId}" \
  "/api/v1/catalog/products/{productId}/price" \
  "/api/v1/catalog/products/{productId}/image" \
  "/api/v1/catalog/moderation/price-changes" \
  "/api/v1/catalog/moderation/price-changes/{skuId}/approve" \
  "/api/v1/catalog/moderation/price-changes/{skuId}/reject" \
  "/api/v1/sales/orders" \
  "/api/v1/sales/orders/{orderId}" \
  "/api/v1/sales/orders/{orderId}/cancel" \
  "/api/v1/sales/orders/{orderId}/refund" \
  "/api/v1/sales/orders/{orderId}/resend-notification" \
  "/api/v1/sales/orders/{orderId}/status-override" \
  "/api/v1/marketing/promotions" \
  "/api/v1/marketing/promotions/{promotionId}" \
  "/api/v1/marketing/promotions/{promotionId}/activate" \
  "/api/v1/marketing/promotions/{promotionId}/pause" \
  "/api/v1/cart/{cartId}/coupon" \
  "/api/v1/reports/sales/summary" \
  "/api/v1/reports/sales/timeseries" \
  "/api/v1/reports/sales/by-vendor" \
  "/api/v1/reports/sales/by-product" \
  "/api/v1/reports/sales/by-category" \
  "/api/v1/reports/sales/export"; do
  if ! printf "%s" "$DOCS_JSON" | grep -Fq "\"$path\""; then
    fail "missing required path in OpenAPI: $path"
  fi
done
note "All 23 Spec-002 paths present in OpenAPI"

if [[ "${SPEC002_SMOKE_OPENAPI_ONLY:-0}" == "1" ]]; then
  note "SPEC002_SMOKE_OPENAPI_ONLY=1 — skipping happy-path checks"
  note "Spec-002 smoke checks passed (OpenAPI only)"
  exit 0
fi

note "Fetching admin token from Keycloak (${KEYCLOAK_URL}, realm ${KEYCLOAK_REALM})"
TOKEN_JSON="$(curl -sf -X POST "${KEYCLOAK_URL}/realms/${KEYCLOAK_REALM}/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=password&client_id=${KEYCLOAK_CLIENT_ID}&username=${SMOKE_USER}&password=${SMOKE_PASS}")" \
  || fail "Keycloak token request (is docker compose keycloak up?)"
TOKEN="$(printf "%s" "$TOKEN_JSON" | python3 -c 'import json,sys; print(json.load(sys.stdin)["access_token"])')" \
  || fail "could not parse access_token"

# Platform admins carry no tenant_id claim; every tenant-scoped call needs X-Tenant-Id.
TENANT_ID="${SMOKE_TENANT_ID:-}"
if [[ -z "$TENANT_ID" ]]; then
  TENANTS_JSON="$(curl -sf "${BASE_URL}/api/v1/tenants" -H "Authorization: Bearer ${TOKEN}")" \
    || fail "tenant list request"
  TENANT_ID="$(printf "%s" "$TENANTS_JSON" | python3 -c 'import json,sys
items = json.load(sys.stdin)
print(items[0]["id"] if items else "")')" || fail "could not parse tenant list"
fi
if [[ -z "$TENANT_ID" ]]; then
  note "No tenant exists in this environment — happy-path checks skipped (create a tenant to enable them)"
  note "Spec-002 smoke checks passed (OpenAPI only)"
  exit 0
fi
note "Using tenant ${TENANT_ID}"

AUTH=(-H "Authorization: Bearer ${TOKEN}" -H "X-Tenant-Id: ${TENANT_ID}")

# expect_status <expected> <description> <curl args...>
expect_status() {
  local expected="$1" description="$2"; shift 2
  local status
  status="$(curl -s -o /dev/null -w "%{http_code}" "$@")"
  [[ "$status" == "$expected" ]] || fail "$description — expected HTTP $expected, got $status"
  note "OK: $description ($status)"
}

FROM="$(date -u -v-30d +%Y-%m-%dT00:00:00Z 2>/dev/null || date -u -d '30 days ago' +%Y-%m-%dT00:00:00Z)"
TO="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
# Promotion window must still be open at delete time (expired promotions are not deletable).
FUTURE="$(date -u -v+1d +%Y-%m-%dT%H:%M:%SZ 2>/dev/null || date -u -d '1 day' +%Y-%m-%dT%H:%M:%SZ)"

# 1) Catalog moderation queue (read)
expect_status 200 "catalog moderation queue lists" "${AUTH[@]}" \
  "${BASE_URL}/api/v1/catalog/moderation/price-changes"

# 2) Sales search (read)
expect_status 200 "sales order search" "${AUTH[@]}" \
  "${BASE_URL}/api/v1/sales/orders?page=0&size=5"

# 3) Promotions: create a DRAFT, list, delete (leaves no residue; DRAFT never applies at checkout)
PROMO_JSON="$(curl -sf -X POST "${BASE_URL}/api/v1/marketing/promotions" "${AUTH[@]}" \
  -H "Content-Type: application/json" \
  -d "{\"name\":\"spec-002 smoke promo\",\"type\":\"PERCENTAGE\",\"value\":10,\"scope\":\"ORDER\",\"startsAt\":\"${TO}\",\"endsAt\":\"${FUTURE}\"}")" \
  || fail "promotion create"
PROMO_ID="$(printf "%s" "$PROMO_JSON" | python3 -c 'import json,sys; print(json.load(sys.stdin)["id"])')" \
  || fail "could not parse created promotion id"
note "OK: promotion created (${PROMO_ID})"
expect_status 200 "promotion list" "${AUTH[@]}" "${BASE_URL}/api/v1/marketing/promotions"
expect_status 204 "promotion delete (cleanup)" -X DELETE "${AUTH[@]}" \
  "${BASE_URL}/api/v1/marketing/promotions/${PROMO_ID}"

# 4) Reports: summary + CSV export headers
expect_status 200 "sales report summary" "${AUTH[@]}" \
  "${BASE_URL}/api/v1/reports/sales/summary?from=${FROM}&to=${TO}"
EXPORT_HEADERS="$(curl -sfI "${BASE_URL}/api/v1/reports/sales/export?report=summary&from=${FROM}&to=${TO}" "${AUTH[@]}")" \
  || fail "sales report CSV export"
printf "%s" "$EXPORT_HEADERS" | grep -qi "content-type: text/csv" || fail "export Content-Type is not text/csv"
printf "%s" "$EXPORT_HEADERS" | grep -qi "content-disposition: attachment" || fail "export missing attachment Content-Disposition"
note "OK: sales report CSV export (text/csv attachment)"

note "Spec-002 smoke checks passed"
