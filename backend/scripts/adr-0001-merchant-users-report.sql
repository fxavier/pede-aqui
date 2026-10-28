-- ADR-0001 step 2/3 — merchant_users backfill report (read-only; safe to run anywhere, any number of times).
--
--   psql "$DB_URL" -f backend/scripts/adr-0001-merchant-users-report.sql
--
-- 1. Ambiguous tenants: tenants with <> 1 vendor that have merchant profiles. V049 did NOT backfill these;
--    a human decides the user → vendor mapping and inserts merchant_users rows explicitly.
\echo '== 1. Tenants with <> 1 vendor and merchant profiles (left untouched by V049) =='
SELECT t.id AS tenant_id, t.slug, t.status,
       (SELECT COUNT(*) FROM vendors v WHERE v.tenant_id = t.id) AS vendor_count,
       (SELECT COUNT(DISTINCT p.id) FROM app_user_profiles p
          JOIN app_user_profile_roles r ON r.app_user_profile_id = p.id
         WHERE p.tenant_id = t.id AND r.role IN ('VENDOR_ADMIN','VENDOR_STAFF')) AS merchant_profiles,
       (SELECT COUNT(DISTINCT p.id) FROM app_user_profiles p
          JOIN app_user_profile_roles r ON r.app_user_profile_id = p.id
         WHERE p.tenant_id = t.id AND r.role IN ('VENDOR_ADMIN','VENDOR_STAFF')
           AND NOT EXISTS (SELECT 1 FROM merchant_users mu WHERE mu.app_user_profile_id = p.id)) AS unresolved_profiles
  FROM tenants t
 WHERE (SELECT COUNT(*) FROM vendors v WHERE v.tenant_id = t.id) <> 1
   AND EXISTS (SELECT 1 FROM app_user_profiles p JOIN app_user_profile_roles r ON r.app_user_profile_id = p.id
                WHERE p.tenant_id = t.id AND r.role IN ('VENDOR_ADMIN','VENDOR_STAFF'))
 ORDER BY t.created_at;

-- 2. Every merchant profile still without an association (what the legacy fallback currently serves).
\echo '== 2. Merchant profiles without a merchant_users row =='
SELECT p.tenant_id, p.id AS app_user_profile_id, p.email, STRING_AGG(r.role, ',' ORDER BY r.role) AS roles,
       (SELECT COUNT(*) FROM vendors v WHERE v.tenant_id = p.tenant_id) AS vendors_in_tenant
  FROM app_user_profiles p
  JOIN app_user_profile_roles r ON r.app_user_profile_id = p.id
 WHERE r.role IN ('VENDOR_ADMIN','VENDOR_STAFF')
   AND NOT EXISTS (SELECT 1 FROM merchant_users mu WHERE mu.app_user_profile_id = p.id)
 GROUP BY p.tenant_id, p.id, p.email
 ORDER BY p.tenant_id, p.email;

-- 3. Integrity: duplicates (expected 0 rows) and cross-tenant rows (expected 0 rows).
\echo '== 3a. Duplicate associations (expected: 0 rows) =='
SELECT tenant_id, app_user_profile_id, vendor_id, COUNT(*)
  FROM merchant_users GROUP BY 1, 2, 3 HAVING COUNT(*) > 1;
\echo '== 3b. Associations whose vendor or profile belongs to another tenant (expected: 0 rows) =='
SELECT mu.id, mu.tenant_id, v.tenant_id AS vendor_tenant_id, p.tenant_id AS profile_tenant_id
  FROM merchant_users mu
  JOIN vendors v ON v.id = mu.vendor_id
  JOIN app_user_profiles p ON p.id = mu.app_user_profile_id
 WHERE v.tenant_id <> mu.tenant_id OR p.tenant_id <> mu.tenant_id;

-- 4. Switch-off criterion for app.merchant-scope.single-vendor-fallback-enabled: this must be 0 in every
--    environment before the flag is set to false (ADR-0001 step 6 then deletes the fallback code).
\echo '== 4. Merchant profiles still relying on the legacy fallback (must be 0 before switching the flag off) =='
SELECT COUNT(DISTINCT p.id) AS profiles_relying_on_fallback
  FROM app_user_profiles p
  JOIN app_user_profile_roles r ON r.app_user_profile_id = p.id
 WHERE r.role IN ('VENDOR_ADMIN','VENDOR_STAFF')
   AND NOT EXISTS (SELECT 1 FROM merchant_users mu WHERE mu.app_user_profile_id = p.id);
