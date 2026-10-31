-- LAC-28 (W7-D): API de avaliações. A tabela `ratings` (V014) passa a ter escritor: o cliente avalia uma encomenda
-- DELIVERED uma vez (loja 1–5 + comentário opcional ≤ 500, estafeta 1–5 opcional), edita durante 24 h, a loja
-- responde uma vez e OPS/ADMIN/SUPPORT ocultam/mostram o comentário. Os agregados (média, contagem) vivem nas linhas
-- de `vendors`/`couriers` e são recalculados na transação de cada avaliação.
--
-- Semântica de ratings.rater_user_id (deixada em aberto por V050/W1-D): é o id do PERFIL (app_user_profiles.id), o
-- mesmo valor que orders.customer_id, nunca o sub Keycloak. Passa a ter FK fk_ratings_rater_user_id.
--   Relatório de órfãos (esperado 0 — não havia escritor):
--     SELECT count(*) FROM ratings r WHERE NOT EXISTS (SELECT 1 FROM app_user_profiles p WHERE p.id = r.rater_user_id);
--   A FK é criada NOT VALID e validada logo a seguir só se não houver órfãos (tabela sem escritor, vazia em todos os
--   ambientes conhecidos — por isso num só passo, e não NOT VALID/VALIDATE em duas migrações como V050/V051). Com
--   órfãos a FK fica NOT VALID (as escritas novas já são verificadas), é emitido um NOTICE e o VALIDATE fica para
--   uma migração posterior depois de corrigido o dado. Nada é apagado.
--
-- Unicidade "uma avaliação por encomenda": já garantida por UNIQUE (order_id, rater_user_id) de V014, porque o
--   avaliador é sempre o cliente da encomenda (o serviço verifica). Não se cria índice novo.
--
-- Colunas novas em ratings: courier_stars (estrelas do estafeta; `stars` continua a ser a nota da LOJA),
--   updated_at (última edição), comment_hidden/comment_hidden_at/comment_hidden_reason (moderação; a nota continua a
--   contar para a média), version (@Version). Linhas antigas só com estafeta (rated_vendor_id NULL) passam a nota
--   para courier_stars.
-- Agregados: vendors.rating / couriers.rating continuam a ser a MÉDIA (2 casas, 0 sem avaliações) — agora real — e
--   ganham rating_count. São recalculados aqui a partir de `ratings` (valores de seed anteriores desaparecem: eram
--   inventados).
--
-- Locking: ADD COLUMN com default constante (PG ≥ 11) e ADD CONSTRAINT CHECK em coluna nova são alterações de
--   catálogo sob ACCESS EXCLUSIVE breve, sem rewrite. ADD CONSTRAINT FOREIGN KEY NOT VALID pede SHARE ROW EXCLUSIVE
--   em ratings e app_user_profiles (bloqueia escritas, não leituras) até ao commit; o VALIDATE lê ratings (vazia).
--   Os UPDATE de agregados tocam todas as linhas de vendors e couriers (tabelas pequenas; lock de linha até ao
--   commit). CREATE INDEX sem CONCURRENTLY (Flyway numa transação, como V046/V057); ratings está vazia.
-- Compatibilidade com a versão anterior da aplicação: total — colunas novas com default ou nullable que ela ignora
--   (JPA valida só colunas mapeadas) e ela não escreve em ratings. Continua a ler vendors.rating/couriers.rating.
-- Rollback (forward-fix numa migração nova, nunca editar esta): ALTER TABLE ratings DROP CONSTRAINT
--   fk_ratings_rater_user_id, DROP COLUMN courier_stars, updated_at, comment_hidden, comment_hidden_at,
--   comment_hidden_reason, version; ALTER TABLE vendors/couriers DROP COLUMN rating_count; DROP INDEX
--   idx_ratings_vendor_created, idx_ratings_rater_user_id.

ALTER TABLE ratings
    ADD COLUMN courier_stars INTEGER,
    ADD COLUMN updated_at TIMESTAMPTZ,
    ADD COLUMN comment_hidden BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN comment_hidden_at TIMESTAMPTZ,
    ADD COLUMN comment_hidden_reason VARCHAR(200),
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE ratings ADD CONSTRAINT ck_ratings_courier_stars CHECK (courier_stars BETWEEN 1 AND 5);

UPDATE ratings SET courier_stars = stars WHERE rated_courier_id IS NOT NULL AND rated_vendor_id IS NULL AND courier_stars IS NULL;
UPDATE ratings SET updated_at = created_at WHERE updated_at IS NULL;
ALTER TABLE ratings ALTER COLUMN updated_at SET NOT NULL;

ALTER TABLE ratings ADD CONSTRAINT fk_ratings_rater_user_id
    FOREIGN KEY (rater_user_id) REFERENCES app_user_profiles (id) NOT VALID;

DO $$
DECLARE
    orphans BIGINT;
BEGIN
    SELECT count(*) INTO orphans FROM ratings r
     WHERE NOT EXISTS (SELECT 1 FROM app_user_profiles p WHERE p.id = r.rater_user_id);
    IF orphans = 0 THEN
        ALTER TABLE ratings VALIDATE CONSTRAINT fk_ratings_rater_user_id;
    ELSE
        RAISE NOTICE 'V072: % ratings rows reference no app_user_profiles row; fk_ratings_rater_user_id left NOT VALID', orphans;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_ratings_rater_user_id ON ratings (rater_user_id);
-- Keyset listing of a vendor's ratings, newest first (vendor and moderation lists).
CREATE INDEX IF NOT EXISTS idx_ratings_vendor_created ON ratings (rated_vendor_id, created_at DESC, id DESC);

ALTER TABLE vendors ADD COLUMN rating_count INTEGER NOT NULL DEFAULT 0;
ALTER TABLE couriers ADD COLUMN rating_count INTEGER NOT NULL DEFAULT 0;

UPDATE vendors v
   SET rating = COALESCE(s.avg_stars, 0),
       rating_count = COALESCE(s.cnt, 0)
  FROM (SELECT vv.id,
               (SELECT ROUND(AVG(r.stars)::numeric, 2) FROM ratings r WHERE r.rated_vendor_id = vv.id) AS avg_stars,
               (SELECT count(*) FROM ratings r WHERE r.rated_vendor_id = vv.id) AS cnt
          FROM vendors vv) s
 WHERE s.id = v.id;

UPDATE couriers c
   SET rating = COALESCE(s.avg_stars, 0),
       rating_count = COALESCE(s.cnt, 0)
  FROM (SELECT cc.id,
               (SELECT ROUND(AVG(r.courier_stars)::numeric, 2) FROM ratings r
                 WHERE r.rated_courier_id = cc.id AND r.courier_stars IS NOT NULL) AS avg_stars,
               (SELECT count(*) FROM ratings r WHERE r.rated_courier_id = cc.id AND r.courier_stars IS NOT NULL) AS cnt
          FROM couriers cc) s
 WHERE s.id = c.id;

COMMENT ON COLUMN ratings.rater_user_id IS 'Profile id (app_user_profiles.id) of the customer who rated; equals orders.customer_id';
COMMENT ON COLUMN ratings.stars IS 'Vendor (store) stars 1-5';
COMMENT ON COLUMN ratings.courier_stars IS 'Courier stars 1-5; NULL when the courier was not rated';
COMMENT ON COLUMN vendors.rating IS 'Average vendor stars (2 decimals, 0 without ratings), maintained by the rating module';
COMMENT ON COLUMN couriers.rating IS 'Average courier stars (2 decimals, 0 without ratings), maintained by the rating module';
