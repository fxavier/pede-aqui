-- LAC-18 (W5-D, ADR-0004): telemóvel verificado do cliente no perfil.
--
-- O login por telemóvel + código SMS acontece no Keycloak (autenticador pede-aqui-phone-otp). O número verificado
-- chega ao backend nas claims OIDC phone_number / phone_number_verified dos clientes de cliente (pede-aqui-web,
-- pede-aqui-mobile) e é copiado para o perfil pelo CustomerProfileProvisioningService (TenantRequestFilter).
--
-- phone_e164        — número em E.164 (ex.: +258841234567), NULL quando não verificado/desconhecido. A coluna
--                     antiga `phone` (texto livre, V002/V012) fica como está: não é verificada e não é chave.
-- phone_verified_at — instante em que o backend viu pela primeira vez o número com phone_number_verified=true.
--
-- Unicidade: um número verificado pertence a no máximo um perfil por tenant (índice único parcial). O Keycloak não
-- impõe unicidade de atributos; o autenticador recusa números ligados a mais do que um utilizador, e o backend
-- ignora (WARN sem PII) uma claim cujo número já pertença a outro perfil do tenant.
--
-- Validação de duplicados antes do índice: a coluna é nova (só NULLs), por isso não pode haver duplicados.
--   SELECT tenant_id, phone_e164, count(*) FROM app_user_profiles
--    WHERE phone_e164 IS NOT NULL GROUP BY 1, 2 HAVING count(*) > 1;   -- 0 linhas
--
-- Locking: ALTER TABLE ... ADD COLUMN (nullable, sem default) e ADD CONSTRAINT CHECK numa coluna só com NULLs são
--   alterações de catálogo sob ACCESS EXCLUSIVE breve (sem rewrite; a validação do CHECK lê a tabela uma vez, sem
--   linhas não-NULL). CREATE UNIQUE INDEX parcial: SHARE em app_user_profiles durante a construção — índice vazio,
--   instantâneo. Sem CONCURRENTLY porque o Flyway corre a migração numa transação (mesmo critério de V046/V057).
-- Compatibilidade: colunas novas e nullable; a versão anterior da aplicação ignora-as (JPA valida só colunas
--   mapeadas). Rollback: forward-fix (DROP INDEX uq_app_user_profiles_tenant_phone_e164; ALTER TABLE ... DROP
--   COLUMN phone_verified_at, DROP COLUMN phone_e164) numa migração nova — nunca editar esta.

ALTER TABLE app_user_profiles
    ADD COLUMN IF NOT EXISTS phone_e164 VARCHAR(16),
    ADD COLUMN IF NOT EXISTS phone_verified_at TIMESTAMPTZ;

ALTER TABLE app_user_profiles
    ADD CONSTRAINT ck_app_user_profiles_phone_e164 CHECK (phone_e164 IS NULL OR phone_e164 ~ '^\+[1-9][0-9]{7,14}$');

CREATE UNIQUE INDEX IF NOT EXISTS uq_app_user_profiles_tenant_phone_e164
    ON app_user_profiles (tenant_id, phone_e164)
    WHERE phone_e164 IS NOT NULL;
