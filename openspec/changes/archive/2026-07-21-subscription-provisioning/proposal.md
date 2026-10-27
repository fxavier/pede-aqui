## Why

When a business subscribes to a plan on `pede-aqui-subscriptions-platform`, no tenant or user is created in the `pede-aqui` backend — the subscribe action only records a `PENDING` `Subscription` row in the marketing DB. The backend already provisions merchants end-to-end (`MerchantRegistrationService` runs a saga: Keycloak user → `Tenant` → `AppUserProfile` → `Vendor`, with compensation on failure), but that endpoint is built for interactive self-service sign-up: the caller must supply a chosen slug, a password, and full legal/address fields, and a network retry cannot distinguish "already provisioned" from "failed". It is unsafe to call service-to-service from the subscriptions platform, which holds only a thin lead and delivers at-least-once.

This change adds a dedicated, idempotent, M2M-secured provisioning endpoint that maps a thin subscription lead to the existing registration saga, so the subscriptions platform can create-or-get a tenant + owner user safely and retryably.

> Contract note: the backend contract below was reconstructed from `backend/src/test/java/com/delivery/auth/MerchantRegistrationServiceTest.java`, `V002__tenants_and_users.sql`, and `application.yml`. Confirm the real signatures in `MerchantRegistrationController`, `MerchantRegistrationRequest`, `MerchantRegistrationService`, `KeycloakAdminService`, and `SecurityConfig` before implementation and reconcile any drift into `specs/tenant-provisioning/spec.md`.

## What Changes

- Add `SubscriptionProvisioningController` exposing `POST /api/v1/provisioning/tenants` (confirm/adjust final path), secured by a Keycloak client-credentials (M2M) token with scope/role `provisioning:write`.
- Add `SubscriptionProvisioningService` that maps the thin lead (`companyName, contactName, email, phone, city, planSlug, billingCycle, externalRef`) to a `MerchantRegistrationRequest` (fills `currency="MZN"`, `country="MZ"`, splits `contactName` → owner first/last, generates a unique slug, no caller password) and delegates to the existing `MerchantRegistrationService` so the saga/compensation is not duplicated.
- Add request/response DTOs and bean-validation.
- Add durable idempotency: a new `provisioning_requests` table keyed by `Idempotency-Key` (+ natural `owner_email` lookup) so replays and duplicate leads return the existing tenant instead of creating a second one.
- Extend `KeycloakAdminService` to create the owner with a required action (`UPDATE_PASSWORD`, optionally `VERIFY_EMAIL`) and trigger the execute-actions onboarding email (no caller-supplied password).
- Add a dedicated Keycloak client `subscriptions-provisioner` (service account with `provisioning:write`) and wire the endpoint authorization in `SecurityConfig`; apply rate limiting via existing `RateLimitConfig`.
- Add Flyway migration `V034__provisioning_idempotency.sql`.

## Capabilities

### New Capabilities

- `tenant-provisioning`: An idempotent, M2M-secured API that creates-or-returns a backend `Tenant` + owner `AppUserProfile` + Keycloak user (+ `Vendor` via the existing saga) from a subscription lead, keyed by an idempotency key.

### Modified Capabilities

- None. The existing interactive `MerchantRegistrationController` behaviour is unchanged; the new endpoint is additive and reuses `MerchantRegistrationService`.

## Impact

- **New files**: `auth/controller/SubscriptionProvisioningController.java`, `auth/service/SubscriptionProvisioningService.java`, request/response DTOs, `auth/entity/ProvisioningRequest.java` + repository, `db/migration/V034__provisioning_idempotency.sql`.
- **Modified files**: `auth/service/KeycloakAdminService.java` (required actions / execute-actions email), `common/security/SecurityConfig.java` (endpoint matcher + M2M role), Keycloak realm config (new `subscriptions-provisioner` client + service-account role).
- **No schema change to existing tables**; one additive table. `spring.jpa.hibernate.ddl-auto=validate` requires the migration to match the new entity.
- **Downstream**: consumed by `pede-aqui-subscriptions-platform` (see that repo's `specs/provisioning/02-subscriptions-provisioning-integration.md`). The HTTP contract in `specs/tenant-provisioning/spec.md` is the seam and must be frozen before the consumer codes against it.
- **Security**: introduces a service-to-service trust path; guarded by M2M scope, rate limiting, and per-email throttling because provisioning may be triggered on unverified leads (provision-on-subscribe).
