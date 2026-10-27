> Contract reconciled against the real backend code (2026-07-21):
> - `MerchantRegistrationRequest` is a 19-field record that REQUIRES `companySlug` and a `password`
>   (min 8). Provisioning therefore generates the slug AND a strong random, never-disclosed password
>   server-side and reuses the saga unchanged; the `UPDATE_PASSWORD` required action and the
>   execute-actions onboarding email are applied immediately after the saga commits.
> - `MerchantRegistrationResponse` is `(tenantId, tenantSlug, userProfileId, email, displayName)`
>   (5 fields, not 3). `ownerUserId` in the provisioning response is the backend `AppUserProfile` id.
> - `provisioning:write` is realized as a Keycloak realm role assigned to the
>   `subscriptions-provisioner` service account (`ROLE_provisioning:write` authority via
>   `JwtRoleConverter`).
> - Typed error codes follow this codebase's snake_case convention: `validation_failed`,
>   `downstream_idp`, `downstream_db`, `conflict`.

## ADDED Requirements

### Requirement: Provisioning endpoint is M2M-secured
The system SHALL expose `POST /api/v1/provisioning/tenants` and SHALL require a valid Keycloak
client-credentials (M2M) access token carrying scope/role `provisioning:write`. The endpoint SHALL
NOT be gated by tenant context (it is a system/cross-tenant operation) and SHALL NOT accept the
interactive user session used by the merchant self-service endpoint.

#### Scenario: Missing token is rejected
- **WHEN** a caller invokes `POST /api/v1/provisioning/tenants` with no `Authorization` header
- **THEN** the endpoint responds `401` and no tenant, user, or Keycloak account is created

#### Scenario: Token without the provisioning scope is rejected
- **WHEN** a caller presents a valid token that lacks `provisioning:write`
- **THEN** the endpoint responds `403` and performs no provisioning

#### Scenario: Valid M2M token is accepted
- **WHEN** a caller presents a valid client-credentials token with `provisioning:write` and a valid body
- **THEN** the endpoint proceeds to provision the tenant

---

### Requirement: Endpoint provisions a tenant and owner from a thin subscription lead
The system SHALL accept a thin lead (`companyName`, `contactName`, `email`, `phone`, optional `city`,
`planSlug`, `billingCycle`, optional `externalRef`) and SHALL create a `Tenant`, an owner
`AppUserProfile`, a Keycloak user, and a `Vendor` by delegating to the existing merchant-registration
saga. The system SHALL derive `currency="MZN"` and `country="MZ"`, split `contactName` into owner
first/last name, and SHALL NOT require the caller to supply a slug or a password.

#### Scenario: New lead is provisioned
- **WHEN** the endpoint receives a valid body for a company not yet provisioned, with a new `Idempotency-Key`
- **THEN** exactly one `Tenant`, one owner `AppUserProfile`, one Keycloak user, and one `Vendor` are created
- **AND** the endpoint responds `201` with `tenantId`, `tenantSlug`, `ownerUserId`, `ownerEmail`, `displayName`, `status`, `provisioned:true`, `alreadyExisted:false`

#### Scenario: Owner is onboarded without a caller-supplied password
- **WHEN** the Keycloak user is created during provisioning
- **THEN** the user is created `enabled=true`, `emailVerified=false`, with a required action to set the password
- **AND** an execute-actions onboarding email is triggered so the owner sets their own password

---

### Requirement: Slug is generated server-side and unique
The system SHALL generate the tenant slug from `companyName` (lowercase, ASCII-folded,
non-alphanumeric runs collapsed to `-`, trimmed, max 60 chars) and SHALL guarantee uniqueness by
appending an incrementing numeric suffix on collision. The caller SHALL NOT provide a slug.

#### Scenario: Slug derived from company name
- **WHEN** a company named "Restaurante Costa Nova" is provisioned and the slug is free
- **THEN** the created tenant slug is `restaurante-costa-nova` and is returned in the response

#### Scenario: Slug collision is resolved deterministically
- **WHEN** a company whose slugified name already exists as a tenant is provisioned
- **THEN** the system appends the next free numeric suffix (e.g. `restaurante-costa-nova-2`) and both tenants exist and are distinct

---

### Requirement: Provisioning is idempotent
The system SHALL treat provisioning as effectively-once. A repeated request carrying the same
`Idempotency-Key`, or a request for an `email` already mapped to a tenant, SHALL return the existing
tenant descriptor without creating a second tenant, user, or Keycloak account. The `Idempotency-Key`
header SHALL be required.

#### Scenario: Missing idempotency key is rejected
- **WHEN** a caller invokes the endpoint without an `Idempotency-Key` header
- **THEN** the endpoint responds `400` and performs no provisioning

#### Scenario: Replay with the same key returns the existing tenant
- **WHEN** a request is retried with the same `Idempotency-Key` after a successful provisioning
- **THEN** the endpoint responds `200` with `alreadyExisted:true`, the same tenant descriptor, and creates no new rows

#### Scenario: Same email with a different key returns the existing tenant
- **WHEN** a company whose `email` already maps to a provisioned tenant is submitted with a different `Idempotency-Key`
- **THEN** the endpoint responds `200` with the existing tenant and creates no duplicate tenant

#### Scenario: Concurrent duplicate requests create one tenant
- **WHEN** two requests for the same `email` are processed concurrently
- **THEN** exactly one tenant is created and both responses describe that same tenant

---

### Requirement: Downstream failure is compensated and retryable
The system SHALL, on a failure partway through the saga (e.g. Keycloak or database error), roll back
so no orphaned Keycloak user or partial tenant remains, SHALL record the attempt as failed, and SHALL
allow a subsequent retry with the same `Idempotency-Key` to succeed.

#### Scenario: Keycloak failure mid-saga leaves no orphan
- **WHEN** the tenant save fails after the Keycloak user was created
- **THEN** the created Keycloak user is deleted (compensation), no tenant is persisted, and the endpoint responds `502` with a typed downstream error code

#### Scenario: Retry after a failure succeeds
- **WHEN** a previously failed request is retried with the same `Idempotency-Key`
- **THEN** the provisioning completes and returns the newly created tenant

---

### Requirement: Interactive merchant registration is unchanged
The system SHALL leave the existing interactive merchant self-service registration endpoint and its
behaviour unchanged; the provisioning endpoint SHALL be additive and reuse the same underlying saga.

#### Scenario: Existing registration path still works
- **WHEN** the interactive merchant registration endpoint is called as before
- **THEN** it behaves exactly as it did prior to this change and its existing tests remain green
