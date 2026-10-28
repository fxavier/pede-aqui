-- ADR-0001 passo 2 (tenant = operador do marketplace): associação explícita utilizador ↔ vendor ↔ tenant.
-- Substitui a heurística "um vendor por tenant" dos guards de comerciante por uma fonte de verdade.
--
-- Locking: CREATE TABLE nova + INSERT ... SELECT (apenas ACCESS SHARE sobre app_user_profiles,
--          app_user_profile_roles e vendors). Não altera tabelas existentes; sem bloqueio de escrita.
-- Compatibilidade: aditiva. A versão anterior da aplicação ignora a tabela; a versão nova lê-a
--          primeiro e mantém a heurística atrás de app.merchant-scope.single-vendor-fallback-enabled.
-- Rollback: DROP TABLE merchant_users; (forward-fix preferido — a tabela não é referenciada por FKs).
--
-- Backfill (só onde não há ambiguidade): para cada tenant com EXACTAMENTE um vendor, todos os perfis
-- desse tenant com papel VENDOR_ADMIN (→ OWNER) ou VENDOR_STAFF (→ STAFF) ficam associados a esse
-- vendor. Um perfil com ambos os papéis fica OWNER. Tenants com 0 ou ≥2 vendors NÃO são tocados —
-- nunca se adivinha; o relatório de ambiguidade abaixo lista-os para decisão humana.
--
-- Relatório de ambiguidade (correr ANTES e DEPOIS; também em backend/scripts/adr-0001-merchant-users-report.sql):
--   SELECT t.id AS tenant_id, t.slug, t.status,
--          (SELECT COUNT(*) FROM vendors v WHERE v.tenant_id = t.id) AS vendor_count,
--          (SELECT COUNT(DISTINCT p.id) FROM app_user_profiles p
--             JOIN app_user_profile_roles r ON r.app_user_profile_id = p.id
--            WHERE p.tenant_id = t.id AND r.role IN ('VENDOR_ADMIN','VENDOR_STAFF')) AS merchant_profiles,
--          (SELECT COUNT(DISTINCT p.id) FROM app_user_profiles p
--             JOIN app_user_profile_roles r ON r.app_user_profile_id = p.id
--            WHERE p.tenant_id = t.id AND r.role IN ('VENDOR_ADMIN','VENDOR_STAFF')
--              AND NOT EXISTS (SELECT 1 FROM merchant_users mu WHERE mu.app_user_profile_id = p.id)) AS unresolved_profiles
--     FROM tenants t
--    WHERE (SELECT COUNT(*) FROM vendors v WHERE v.tenant_id = t.id) <> 1
--      AND EXISTS (SELECT 1 FROM app_user_profiles p JOIN app_user_profile_roles r ON r.app_user_profile_id = p.id
--                   WHERE p.tenant_id = t.id AND r.role IN ('VENDOR_ADMIN','VENDOR_STAFF'))
--    ORDER BY t.created_at;
--
-- Validação da UNIQUE (tenant_id, app_user_profile_id, vendor_id): a tabela nasce vazia e o backfill
-- agrega por (perfil, vendor), logo não pode produzir duplicados; a query de controlo é
--   SELECT tenant_id, app_user_profile_id, vendor_id, COUNT(*) FROM merchant_users
--    GROUP BY 1,2,3 HAVING COUNT(*) > 1;   -- esperado: 0 linhas

CREATE TABLE merchant_users (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    vendor_id UUID NOT NULL REFERENCES vendors(id) ON DELETE CASCADE,
    app_user_profile_id UUID NOT NULL REFERENCES app_user_profiles(id) ON DELETE CASCADE,
    role VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_merchant_users_role CHECK (role IN ('OWNER', 'STAFF')),
    CONSTRAINT uq_merchant_users_tenant_profile_vendor UNIQUE (tenant_id, app_user_profile_id, vendor_id)
);

-- Caminho quente dos guards: (tenant, perfil) → vendors; e limpeza/relatórios por vendor.
CREATE INDEX idx_merchant_users_tenant_profile ON merchant_users (tenant_id, app_user_profile_id);
CREATE INDEX idx_merchant_users_vendor ON merchant_users (vendor_id);

INSERT INTO merchant_users (id, tenant_id, vendor_id, app_user_profile_id, role, created_at, updated_at)
SELECT gen_random_uuid(),
       p.tenant_id,
       sv.vendor_id,
       p.id,
       CASE WHEN BOOL_OR(r.role = 'VENDOR_ADMIN') THEN 'OWNER' ELSE 'STAFF' END,
       NOW(),
       NOW()
  FROM app_user_profiles p
  JOIN app_user_profile_roles r
    ON r.app_user_profile_id = p.id
   AND r.role IN ('VENDOR_ADMIN', 'VENDOR_STAFF')
  JOIN (SELECT tenant_id, (ARRAY_AGG(id))[1] AS vendor_id   -- exactly one element (HAVING COUNT(*) = 1)
          FROM vendors
         GROUP BY tenant_id
        HAVING COUNT(*) = 1) sv
    ON sv.tenant_id = p.tenant_id
 GROUP BY p.tenant_id, p.id, sv.vendor_id
ON CONFLICT ON CONSTRAINT uq_merchant_users_tenant_profile_vendor DO NOTHING;

-- Regista no log do Flyway (NOTICE) quantos tenants ficaram por resolver, para o relatório do incremento.
DO $$
DECLARE
    backfilled BIGINT;
    ambiguous_tenants BIGINT;
    unresolved_profiles BIGINT;
BEGIN
    SELECT COUNT(*) INTO backfilled FROM merchant_users;
    SELECT COUNT(*) INTO ambiguous_tenants
      FROM tenants t
     WHERE (SELECT COUNT(*) FROM vendors v WHERE v.tenant_id = t.id) <> 1
       AND EXISTS (SELECT 1 FROM app_user_profiles p
                     JOIN app_user_profile_roles r ON r.app_user_profile_id = p.id
                    WHERE p.tenant_id = t.id AND r.role IN ('VENDOR_ADMIN', 'VENDOR_STAFF'));
    SELECT COUNT(DISTINCT p.id) INTO unresolved_profiles
      FROM app_user_profiles p
      JOIN app_user_profile_roles r ON r.app_user_profile_id = p.id
     WHERE r.role IN ('VENDOR_ADMIN', 'VENDOR_STAFF')
       AND NOT EXISTS (SELECT 1 FROM merchant_users mu WHERE mu.app_user_profile_id = p.id);
    RAISE NOTICE 'ADR-0001 V049 merchant_users: % rows backfilled; % tenant(s) with <> 1 vendor left untouched; % merchant profile(s) still unresolved (see ambiguity report)',
        backfilled, ambiguous_tenants, unresolved_profiles;
END $$;
