## 1. Confirm the existing contract (do first)

- [x] 1.1 Read `auth/controller/MerchantRegistrationController.java` and record the real path, method, and security of the interactive endpoint
- [x] 1.2 Read `auth/dto/MerchantRegistrationRequest.java` / `MerchantRegistrationResponse.java` and confirm the exact field list/order (the reconstructed 19-field record) and the two trailing nullable fields
- [x] 1.3 Read `auth/service/MerchantRegistrationService.java` and confirm the saga order, uniqueness pre-checks, roles assigned, and compensation behaviour
- [x] 1.4 Read `auth/service/KeycloakAdminService.java` and confirm `createUser(...)` / `deleteUser(...)` signatures and whether required-actions / execute-actions email are already supported
- [x] 1.5 Read `common/security/SecurityConfig.java`, `JwtRoleConverter`, `MarketplaceRole`, `TenantContext`, `RateLimitConfig` and confirm how roles/scopes are mapped and how matchers are declared
- [x] 1.6 Reconcile any drift into `specs/tenant-provisioning/spec.md` before writing code

## 2. Idempotency persistence

- [x] 2.1 Add `db/migration/V034__provisioning_idempotency.sql` creating `provisioning_requests` (id, idempotency_key unique, owner_email, external_ref, tenant_id FK, status, response_json, created_at, updated_at) + indexes
- [x] 2.2 Add `ProvisioningRequest` entity + `ProvisioningRequestRepository` (lookup by idempotency_key and by owner_email)
- [x] 2.3 Verify `ddl-auto=validate` still boots (entity matches migration)

## 3. Keycloak onboarding (no caller password)

- [x] 3.1 Extend `KeycloakAdminService` to create the owner with `enabled=true`, `emailVerified=false`, required action `UPDATE_PASSWORD` (optionally `VERIFY_EMAIL`)
- [x] 3.2 Add `sendExecuteActionsEmail(userId, actions)` (or set at create time) to trigger the set-password onboarding email
- [x] 3.3 Ensure compensation still deletes the Keycloak user on downstream failure

## 4. Provisioning service (mapping + idempotency)

- [x] 4.1 Add `SubscriptionProvisioningService` mapping the thin lead → `MerchantRegistrationRequest` (`currency="MZN"`, `country="MZ"`, split `contactName`, generated slug, null legal/address)
- [x] 4.2 Implement server-side slug generation (slugify `companyName`, max 60) with collision suffixing against `existsBySlug`
- [x] 4.3 Implement the idempotency branch: new key → run saga + persist; replayed key → return cached descriptor; known `owner_email` → return existing tenant (all without creating duplicates)
- [x] 4.4 Delegate the actual create to `MerchantRegistrationService.register(...)` (reuse the saga/compensation)

## 5. Controller + DTOs + security

- [x] 5.1 Add request DTO (companyName, contactName, email, phone, city?, planSlug, billingCycle, externalRef?) with bean-validation, and response DTO (tenantId, tenantSlug, ownerUserId, ownerEmail, displayName, status, provisioned, alreadyExisted)
- [x] 5.2 Add `SubscriptionProvisioningController` — `POST /api/v1/provisioning/tenants` requiring `Idempotency-Key`; `201` on first, `200` on replay/already-existed; typed error codes (`VALIDATION`, `DOWNSTREAM_IDP`, `DOWNSTREAM_DB`, `CONFLICT`)
- [x] 5.3 Add the endpoint matcher to `SecurityConfig` requiring scope/role `provisioning:write`; ensure it is NOT gated by `TenantContext`
- [x] 5.4 Add the `subscriptions-provisioner` Keycloak client (service account + `provisioning:write` role) to the realm config; document the client-credentials setup
- [x] 5.5 Apply rate limiting (`RateLimitConfig`) + per-email-domain throttle + hourly tenant-creation cap with alerting

## 6. Tests

- [x] 6.1 Unit: mapping (contactName split, MZN/MZ defaults, slug gen + collision) and idempotency branch (new / replay / email-hit) with Mockito, mirroring `MerchantRegistrationServiceTest` style
- [x] 6.2 `@WebMvcTest`: authz (401 no token / 403 missing scope / 201 / 200) and validation (400)
- [x] 6.3 Testcontainers (reuse `PostgresTestContainerConfig`): real saga + idempotency table; assert single-tenant invariant under concurrent duplicate calls
- [x] 6.4 Keep `MerchantRegistrationServiceTest` and the rest of the suite green; `./mvnw verify` passes

## 7. Publish

- [x] 7.1 Confirm `/swagger-ui.html` shows the endpoint with the M2M security scheme
- [x] 7.2 Freeze the HTTP contract and notify the consumer repo (`pede-aqui-subscriptions-platform` spec 02)
