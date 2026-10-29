#!/usr/bin/env bash
# shellcheck shell=bash
#
# DEV/TEST ONLY. Ensures a password-grant (ROPC) client exists in a LOCAL Keycloak realm so scripts can
# fetch tokens without a browser. The real backoffice client `delivery-app` is Authorization Code + PKCE
# only (directAccessGrantsEnabled=false, LAC-10), and ROPC would also bypass the realm's TOTP browser flow
# for ADMIN/FINANCE/OPS — so this client must never exist outside local dev.
#
# The client is created through the Keycloak admin API (realm import cannot add clients from the
# dev-users seed file) and copies the protocol mappers (tenant_id, vendor_id) from `delivery-app`, so its
# tokens carry the same claims the backoffice BFF receives.
#
# Usage (sourced):  source scripts/lib/keycloak-dev-ropc-client.sh
#                   ensure_dev_ropc_client "$KEYCLOAK_URL" "$KEYCLOAK_REALM"   # prints the client id
# Env: KEYCLOAK_ADMIN_USER / KEYCLOAK_ADMIN_PASSWORD (compose bootstrap admin, default admin/admin),
#      DEV_ROPC_CLIENT_ID (default backoffice-dev-cli). Refuses non-localhost URLs.

DEV_ROPC_CLIENT_ID="${DEV_ROPC_CLIENT_ID:-backoffice-dev-cli}"

ensure_dev_ropc_client() {
  local kc_url="$1" realm="$2"
  case "$kc_url" in
    http://localhost:*|http://127.0.0.1:*) ;;
    *) echo "ensure_dev_ropc_client: refusing non-local Keycloak URL ${kc_url}" >&2; return 1 ;;
  esac

  local admin_token
  admin_token="$(curl -sf -X POST "${kc_url}/realms/master/protocol/openid-connect/token" \
      -H "Content-Type: application/x-www-form-urlencoded" \
      --data-urlencode "grant_type=password" --data-urlencode "client_id=admin-cli" \
      --data-urlencode "username=${KEYCLOAK_ADMIN_USER:-admin}" \
      --data-urlencode "password=${KEYCLOAK_ADMIN_PASSWORD:-admin}" \
    | python3 -c 'import json,sys; print(json.load(sys.stdin)["access_token"])')" || {
    echo "ensure_dev_ropc_client: could not get a master-realm admin token" >&2; return 1; }

  local existing
  existing="$(curl -sf -H "Authorization: Bearer ${admin_token}" \
    "${kc_url}/admin/realms/${realm}/clients?clientId=${DEV_ROPC_CLIENT_ID}")" || return 1
  if [[ "$existing" != "[]" ]]; then
    echo "$DEV_ROPC_CLIENT_ID"
    return 0
  fi

  local template body
  template="$(curl -sf -H "Authorization: Bearer ${admin_token}" \
    "${kc_url}/admin/realms/${realm}/clients?clientId=delivery-app")" || return 1
  body="$(printf "%s" "$template" | DEV_ROPC_CLIENT_ID="$DEV_ROPC_CLIENT_ID" python3 -c '
import json, os, sys
source = json.load(sys.stdin)[0]
mappers = [{k: v for k, v in m.items() if k != "id"} for m in source.get("protocolMappers", [])]
print(json.dumps({
    "clientId": os.environ["DEV_ROPC_CLIENT_ID"],
    "name": "DEV ONLY - scripted password grant",
    "description": "Created by scripts/lib/keycloak-dev-ropc-client.sh for local smoke tests. Never in prod.",
    "enabled": True,
    "publicClient": True,
    "directAccessGrantsEnabled": True,
    "standardFlowEnabled": False,
    "implicitFlowEnabled": False,
    "protocolMappers": mappers,
}))')" || return 1

  curl -sf -X POST -H "Authorization: Bearer ${admin_token}" -H "Content-Type: application/json" \
    -d "$body" "${kc_url}/admin/realms/${realm}/clients" >/dev/null || {
    echo "ensure_dev_ropc_client: client creation failed" >&2; return 1; }
  echo "$DEV_ROPC_CLIENT_ID"
}
