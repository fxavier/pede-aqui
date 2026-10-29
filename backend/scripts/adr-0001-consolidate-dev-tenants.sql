-- ADR-0001 step 5 — consolidate legacy per-merchant tenants into the operator tenant (dev/staging ONLY).
--
-- NOT a Flyway migration on purpose: it is per-environment, needs a human decision (which tenants are
-- legacy merchant tenants) and an explicit approval before the apply run. Production has no legacy data.
--
-- APPROVAL NOTE: run the dry-run first, paste its output in the increment report, and only run with
-- mode=apply after the owner approves the counts and the legacy tenant list. Never run on production.
--
-- What it does, for every legacy tenant (all tenants except the operator, except tenants already
-- CONSOLIDATED, optionally restricted to an explicit list):
--   1. verifies the legacy tenant has exactly ONE vendor (otherwise it is skipped and reported);
--   2. verifies no UNIQUE (tenant_id, …) collision would occur after moving rows into the operator tenant
--      (categories.slug, app_user_profiles.keycloak_user_id / nif, verticals.slug, coupons.code,
--      promotion.code, orders.checkout_idempotency_key, payments.idempotency_key, refunds.idempotency_key,
--      couriers.user_profile_id, inventory_items.sku_id, deliveries.order_id …) — on any collision the whole run
--      aborts (nothing is guessed; fix the data, re-run);
--   3. moves every row of every table with a tenant_id column (discovered from information_schema) to the
--      operator tenant, EXCEPT: tenants (the row itself), fee_policies (UNIQUE per tenant: the operator keeps
--      its own policy; the legacy one stays attached to the inactive tenant), provisioning_requests (ledger of
--      the M2M path; keeps pointing at the tenant it provisioned) and audit_logs (history is not rewritten);
--   4. inserts the merchant_users rows for the moved VENDOR_ADMIN/VENDOR_STAFF profiles → the legacy tenant's
--      single vendor (idempotent, ON CONFLICT DO NOTHING);
--   5. marks the legacy tenant status = 'CONSOLIDATED' (not deleted — rollback and audit stay possible);
--   6. prints the Keycloak users whose tenant_id attribute must be rewritten to the operator tenant
--      (done out-of-band with the Admin REST API / kcadm — see the NOTICE at the end). Old tokens expire by TTL.
--
-- Idempotent: a second run finds no legacy tenant with status <> 'CONSOLIDATED' and does nothing.
--
-- Usage (psql; ON_ERROR_STOP so a collision aborts the transaction):
--   dry-run (SELECT counts only, always rolled back):
--     psql "$DB_URL" -v ON_ERROR_STOP=1 -v mode=dry-run \
--          -v operator=5e6d0c1a-7b2f-4f3e-9c8a-0d1e2f3a4b5c -v legacy_tenants='' \
--          -f backend/scripts/adr-0001-consolidate-dev-tenants.sql
--   apply (after approval):
--     psql "$DB_URL" -v ON_ERROR_STOP=1 -v mode=apply \
--          -v operator=5e6d0c1a-7b2f-4f3e-9c8a-0d1e2f3a4b5c -v legacy_tenants='<uuid>,<uuid>' \
--          -f backend/scripts/adr-0001-consolidate-dev-tenants.sql
--   legacy_tenants='' means "every tenant except the operator"; a comma-separated UUID list restricts the run.
--
-- Rollback: nothing is deleted. To undo one tenant, UPDATE … SET tenant_id = :legacy WHERE id IN (ids logged
-- in consolidation_log) and set its status back to ACTIVE; the script records every moved row in
-- adr0001_consolidation_log (created on first apply) exactly for that purpose.

\set ON_ERROR_STOP on
\if :{?mode}
\else
\set mode dry-run
\endif
\if :{?legacy_tenants}
\else
\set legacy_tenants ''
\endif
\if :{?operator}
\else
\set operator 5e6d0c1a-7b2f-4f3e-9c8a-0d1e2f3a4b5c
\endif
\echo mode: :mode  operator: :operator  legacy_tenants: :legacy_tenants

BEGIN;

-- Session parameters readable from PL/pgSQL (psql variables are not visible inside DO blocks).
SELECT set_config('adr0001.operator', :'operator', true);
SELECT set_config('adr0001.legacy_tenants', :'legacy_tenants', true);
SELECT set_config('adr0001.mode', :'mode', true);

CREATE TABLE IF NOT EXISTS adr0001_consolidation_log (
    run_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    legacy_tenant_id UUID NOT NULL,
    table_name TEXT NOT NULL,
    row_id UUID,
    note TEXT
);

DO $$
DECLARE
    operator UUID := current_setting('adr0001.operator')::uuid;
    apply BOOLEAN := current_setting('adr0001.mode') = 'apply';
    legacy_filter TEXT := NULLIF(current_setting('adr0001.legacy_tenants'), '');
    legacy RECORD;
    tbl RECORD;
    moved BIGINT;
    total_moved BIGINT;
    vendor_count INT;
    legacy_vendor UUID;
    collisions INT;
    collision_total INT := 0;
    skipped_tables TEXT[] := ARRAY['tenants', 'fee_policies', 'provisioning_requests', 'audit_logs', 'adr0001_consolidation_log'];
    -- UNIQUE (tenant_id, col) constraints that can collide when two tenants merge.
    unique_checks TEXT[][] := ARRAY[
        ARRAY['categories', 'slug'],
        -- skus.sku_code is unique per (tenant, vendor) since V056 and a legacy tenant moves with its own single
        -- vendor, so SKU codes can no longer collide with the stores of the operator: no check needed.
        ARRAY['app_user_profiles', 'keycloak_user_id'],
        ARRAY['app_user_profiles', 'nif'],
        ARRAY['verticals', 'slug'],
        ARRAY['coupons', 'code'],
        ARRAY['promotion', 'code'],
        ARRAY['orders', 'checkout_idempotency_key'],
        ARRAY['payments', 'idempotency_key'],
        ARRAY['refunds', 'idempotency_key'],
        ARRAY['couriers', 'user_profile_id'],
        ARRAY['inventory_items', 'sku_id'],
        ARRAY['deliveries', 'order_id'],
        ARRAY['user_notification_preferences', 'user_profile_id'],
        ARRAY['orders', 'delivery_confirmation_code_hash']
    ];
    chk TEXT[];
BEGIN
    IF NOT EXISTS (SELECT 1 FROM tenants WHERE id = operator) THEN
        RAISE EXCEPTION 'ADR-0001: operator tenant % does not exist (run V045 first)', operator;
    END IF;
    RAISE NOTICE 'ADR-0001 consolidation — mode=% operator=%', CASE WHEN apply THEN 'APPLY' ELSE 'DRY-RUN' END, operator;

    FOR legacy IN
        SELECT t.id, t.slug, t.status
          FROM tenants t
         WHERE t.id <> operator
           AND t.status <> 'CONSOLIDATED'
           AND (legacy_filter IS NULL OR t.id = ANY (string_to_array(legacy_filter, ',')::uuid[]))
         ORDER BY t.created_at
    LOOP
        SELECT COUNT(*), MIN(id::text)::uuid INTO vendor_count, legacy_vendor FROM vendors WHERE tenant_id = legacy.id;
        IF vendor_count <> 1 THEN
            RAISE NOTICE '  SKIP tenant % (%): % vendor(s) — needs a manual decision', legacy.slug, legacy.id, vendor_count;
            CONTINUE;
        END IF;

        -- (2) collision check against the operator tenant.
        collisions := 0;
        FOREACH chk SLICE 1 IN ARRAY unique_checks LOOP
            IF EXISTS (SELECT 1 FROM information_schema.columns
                        WHERE table_schema = 'public' AND table_name = chk[1] AND column_name = chk[2]) THEN
                EXECUTE format(
                    'SELECT COUNT(*) FROM %I l WHERE l.tenant_id = $1 AND l.%I IS NOT NULL
                        AND EXISTS (SELECT 1 FROM %I o WHERE o.tenant_id = $2 AND o.%I = l.%I)',
                    chk[1], chk[2], chk[1], chk[2], chk[2])
                   INTO moved USING legacy.id, operator;
                IF moved > 0 THEN
                    RAISE NOTICE '  COLLISION tenant % table %.%: % row(s) already exist in the operator tenant', legacy.slug, chk[1], chk[2], moved;
                    collisions := collisions + moved;
                END IF;
            END IF;
        END LOOP;
        collision_total := collision_total + collisions;

        -- (3) per-table counts (dry-run) / moves (apply).
        total_moved := 0;
        FOR tbl IN
            SELECT c.table_name
              FROM information_schema.columns c
              JOIN information_schema.tables t ON t.table_schema = c.table_schema AND t.table_name = c.table_name
             WHERE c.table_schema = 'public' AND c.column_name = 'tenant_id' AND t.table_type = 'BASE TABLE'
               AND NOT (c.table_name = ANY (skipped_tables))
             ORDER BY c.table_name
        LOOP
            IF apply AND collisions = 0 THEN
                IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'public' AND table_name = tbl.table_name AND column_name = 'id') THEN
                    EXECUTE format('INSERT INTO adr0001_consolidation_log (legacy_tenant_id, table_name, row_id)
                                    SELECT $1, %L, id FROM %I WHERE tenant_id = $1', tbl.table_name, tbl.table_name) USING legacy.id;
                END IF;
                EXECUTE format('UPDATE %I SET tenant_id = $1 WHERE tenant_id = $2', tbl.table_name) USING operator, legacy.id;
                GET DIAGNOSTICS moved = ROW_COUNT;
            ELSE
                EXECUTE format('SELECT COUNT(*) FROM %I WHERE tenant_id = $1', tbl.table_name) INTO moved USING legacy.id;
            END IF;
            IF moved > 0 THEN
                RAISE NOTICE '  tenant % table %: % row(s)', legacy.slug, tbl.table_name, moved;
                total_moved := total_moved + moved;
            END IF;
        END LOOP;

        IF apply AND collisions = 0 THEN
            -- (4) merchant_users for the moved merchant profiles → the legacy tenant's single vendor.
            INSERT INTO merchant_users (id, tenant_id, vendor_id, app_user_profile_id, role, created_at, updated_at)
            SELECT gen_random_uuid(), operator, legacy_vendor, p.id,
                   CASE WHEN BOOL_OR(r.role = 'VENDOR_ADMIN') THEN 'OWNER' ELSE 'STAFF' END, NOW(), NOW()
              FROM app_user_profiles p
              JOIN app_user_profile_roles r ON r.app_user_profile_id = p.id AND r.role IN ('VENDOR_ADMIN', 'VENDOR_STAFF')
             WHERE p.id IN (SELECT row_id FROM adr0001_consolidation_log WHERE legacy_tenant_id = legacy.id AND table_name = 'app_user_profiles')
             GROUP BY p.id
            ON CONFLICT ON CONSTRAINT uq_merchant_users_tenant_profile_vendor DO NOTHING;
            GET DIAGNOSTICS moved = ROW_COUNT;
            -- Rows that already pointed at the legacy tenant (V049 backfill) follow the move above (tenant_id updated).
            UPDATE merchant_users SET tenant_id = operator WHERE tenant_id = legacy.id;
            RAISE NOTICE '  tenant % merchant_users rows added: %', legacy.slug, moved;
            -- (5) deactivate, never delete.
            UPDATE tenants SET status = 'CONSOLIDATED', updated_at = NOW() WHERE id = legacy.id;
            INSERT INTO adr0001_consolidation_log (legacy_tenant_id, table_name, row_id, note)
            VALUES (legacy.id, 'tenants', legacy.id, format('consolidated into %s; %s rows moved; vendor %s', operator, total_moved, legacy_vendor));
            RAISE NOTICE '  tenant % CONSOLIDATED (% rows moved)', legacy.slug, total_moved;
        ELSE
            RAISE NOTICE '  tenant % would move % row(s)%', legacy.slug, total_moved,
                CASE WHEN collisions > 0 THEN ' — BLOCKED by collisions' ELSE '' END;
        END IF;
    END LOOP;

    IF apply AND collision_total > 0 THEN
        RAISE EXCEPTION 'ADR-0001 consolidation aborted: % collision(s) — resolve them and re-run (transaction rolled back)', collision_total;
    END IF;
END $$;

-- (6) Keycloak attribute rewrite list: users of consolidated tenants whose token still carries the old tenant_id.
\echo '== Keycloak users whose tenant_id attribute must become the operator tenant (Admin REST: PUT /admin/realms/{realm}/users/{id} with attributes.tenant_id) =='
SELECT DISTINCT p.keycloak_user_id, p.email, l.legacy_tenant_id
  FROM adr0001_consolidation_log l
  JOIN app_user_profiles p ON p.id = l.row_id
 WHERE l.table_name = 'app_user_profiles'
 ORDER BY l.legacy_tenant_id, p.email;

-- Dry-run always rolls back; apply commits only when the DO block did not raise.
SELECT (:'mode' = 'apply') AS do_commit,
       CASE WHEN :'mode' = 'apply' THEN 'APPLY: committing' ELSE 'DRY-RUN: rolling back' END AS outcome \gset
\echo :outcome
\if :do_commit
COMMIT;
\else
ROLLBACK;
\endif
