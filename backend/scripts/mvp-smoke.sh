#!/usr/bin/env bash

set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"

echo "[smoke] Checking backend health"
curl -sf "${BASE_URL}/actuator/health" >/dev/null

echo "[smoke] Checking OpenAPI endpoint"
curl -sf "${BASE_URL}/api-docs" >/dev/null

echo "[smoke] Verifying critical MVP paths in OpenAPI"
DOCS_JSON="$(curl -sf "${BASE_URL}/api-docs")"
for path in \
  "/api/v1/search/vendors" \
  "/api/v1/checkout" \
  "/api/v1/payments/{paymentId}/confirm" \
  "/api/v1/vendor/orders/{orderId}/accept" \
  "/api/v1/vendor/orders/{orderId}/ready-for-pickup" \
  "/api/v1/vendors" \
  "/api/v1/dispatch-jobs/assign" \
  "/api/v1/dispatch-jobs/{jobId}/accept" \
  "/api/v1/deliveries/{deliveryId}/complete" \
  "/api/v1/finance/summary" \
  "/api/v1/support/tickets" \
  "/api/v1/notifications"; do
  if ! printf "%s" "$DOCS_JSON" | grep -Fq "\"$path\""; then
    echo "[smoke] Missing required path in OpenAPI: $path" >&2
    exit 1
  fi
done

echo "[smoke] MVP smoke checks passed"
