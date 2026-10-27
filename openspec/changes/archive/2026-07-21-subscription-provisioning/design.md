## Context

The `pede-aqui` backend already creates merchants end-to-end. `MerchantRegistrationService`
(constructor `(TenantRepository, AppUserProfileRepository, KeycloakAdminService, VendorRepository)`)
runs a local **saga**: create Keycloak user → save `Tenant` → save `AppUserProfile` → save `Vendor`,
and on any failure **compensates** by calling `keycloakAdminService.deleteUser(userId)`
(`MerchantRegistrationServiceTest` asserts both the happy path and the compensation). Uniqueness is
pre-checked via `tenantRepository.existsBySlug(slug)` and `appUserProfileRepository.existsByEmail`.
`MerchantRegistrationRequest` is a 19-field record (with a REQUIRED `password`, min 8);
`MerchantRegistrationResponse = (tenantId, tenantSlug, userProfileId, email, displayName)`. Keycloak M2M admin is already configured (`app.keycloak.admin.*`, client
`delivery-admin-service`, realm `delivery`); the API is a JWT resource server. Tenant/user tables come
from `V002__tenants_and_users.sql`.

The subscriptions platform holds only a thin lead (`companyName, contactName, email, phone, city,
planSlug, billingCycle`) and calls service-to-service with at-least-once delivery and no interactive
password. Reusing the interactive endpoint as-is is unsafe (client-chosen slug collides; no password
to supply; retries can't tell already-provisioned from failed).

## Goals / Non-Goals

**Goals:**
- A dedicated, **idempotent** endpoint the subscriptions platform can safely retry.
- **M2M-secured** (Keycloak client-credentials, scope `provisioning:write`), not the interactive path.
- Map the thin lead to the existing saga; **reuse `MerchantRegistrationService`**, don't fork it.
- Generate the slug server-side; onboard the owner via set-password/verify-email (no caller password).
- Durable, auditable idempotency; safe under concurrent duplicate calls.

**Non-Goals:**
- No recurring billing / payments (owned by the subscriptions platform + PSP).
- No change to the interactive `MerchantRegistrationController`.
- No new identity provider; keep Keycloak.

## Decisions

**1. Dedicated endpoint + service that delegates to the existing saga.**
Add `SubscriptionProvisioningService` that adapts the thin request → `MerchantRegistrationRequest`
(`currency="MZN"`, `country="MZ"`, `contactName` split into owner first/last, generated slug, null
legal/address) and calls `MerchantRegistrationService.register(...)`. The saga/compensation logic is
not duplicated. Alternative (reuse the interactive controller directly) rejected — it forces a
client-chosen slug and a password the caller does not have.

**2. Idempotency via caller `Idempotency-Key` + natural `owner_email`.**
First call runs the saga and persists the key → `201`. A replay with the same key, or a different key
for an already-provisioned `owner_email`, returns the existing tenant → `200` (`alreadyExisted:true`),
no new rows. This turns at-least-once delivery into effectively-once provisioning. Durable in a
`provisioning_requests` table (survives restarts, auditable), not in memory.

**3. Server-side slug generation with collision suffixing.**
Slugify `companyName` (`lower, ascii-fold, [^a-z0-9]+→'-'`, trim, max 60); on `existsBySlug`
collision append `-2`, `-3`, …. Return the final slug so the caller can store it. The caller never
sends a slug.

**4. Password-less onboarding.**
Create the Keycloak user `enabled=true`, `emailVerified=false`, with required action
`UPDATE_PASSWORD` (optionally `VERIFY_EMAIL`), then trigger the execute-actions email so the owner
sets their own password. Extends `KeycloakAdminService`. Alternative (caller-generated temp password)
rejected — spreads secrets across services and needs a delivery channel anyway.

**5. Trigger is the caller's concern; the endpoint is trigger-agnostic.**
The requirement is "provision on subscribe". The endpoint stays idempotent and stateless about *when*
it is called; the subscriptions platform decides via a `PROVISION_ON` flag (default `subscribe`).
This honours the requirement while letting the business move to an activation-gated flow by config,
not a rewrite. Because on-subscribe fires for unverified leads, the endpoint enforces rate limiting
(existing `RateLimitConfig`), per-email-domain throttling, and a tenants-per-hour cap with alerting.

## Risks / Trade-offs

- **Abuse / cost on provision-on-subscribe** — every lead can create a live tenant + Keycloak user.
  → Mitigation: M2M scope, rate limiting, per-email throttling, hard hourly cap + alert; the caller
  can switch to `PROVISION_ON=activate` without backend changes.
- **Concurrent duplicate calls** (same email, two in-flight requests) could race two tenants.
  → Mitigation: unique constraint on `idempotency_key`; transactional check-or-insert on
  `owner_email`; Testcontainers concurrency test asserts the single-tenant invariant.
- **Partial saga failure** — Keycloak user created but tenant save fails.
  → Existing compensation deletes the Keycloak user; `provisioning_requests.status=FAILED`; a retry
  with the same key can succeed.
- **Contract drift** — the contract was reconstructed from tests/migration/config. → The first
  implementation task is to read the real `main/java` classes and reconcile before coding.

## Migration Plan

1. Confirm real signatures in `MerchantRegistrationController/Request/Service`, `KeycloakAdminService`,
   `SecurityConfig`; reconcile `specs/tenant-provisioning/spec.md`.
2. Add `V034__provisioning_idempotency.sql` (+ `ProvisioningRequest` entity/repository).
3. Extend `KeycloakAdminService` (required actions + execute-actions email).
4. Add `SubscriptionProvisioningService` (mapping + idempotency) delegating to `MerchantRegistrationService`.
5. Add `SubscriptionProvisioningController` + DTOs + bean-validation.
6. Wire `SecurityConfig` matcher + `subscriptions-provisioner` client/role + rate limit.
7. Tests: unit + `@WebMvcTest` slice + Testcontainers; keep `MerchantRegistrationServiceTest` green.
8. Publish OpenAPI; freeze the HTTP contract for the consumer.

Rollback: the endpoint is additive and behind M2M auth; disabling the new controller/route or
revoking the `subscriptions-provisioner` client credentials disables provisioning without touching the
interactive path. The `V034` table is additive and inert if the endpoint is disabled.
