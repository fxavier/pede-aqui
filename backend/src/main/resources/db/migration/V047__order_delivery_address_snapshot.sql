-- INC-12 (AS-02, FR-003) — snapshot imutável da morada de entrega na encomenda.
--
-- A morada é copiada de customer_addresses no checkout (texto + coordenadas + instruções); a morada de
-- origem pode depois ser editada ou apagada sem afetar a encomenda. delivery_address_id é só
-- rastreabilidade (sem FK, precisamente porque a origem pode ser apagada). Encomendas anteriores a
-- INC-12, e as criadas pela janela de compatibilidade sem addressId (docs/api/API-MIGRATION.md), ficam
-- com todas as colunas a NULL.
--
-- Locking: ADD COLUMN nullable sem default é só metadados (ACCESS EXCLUSIVE breve, sem reescrita da
-- tabela orders). CREATE TRIGGER: SHARE ROW EXCLUSIVE breve (bloqueia escritas em orders por instantes).
-- Compatibilidade com a versão anterior da aplicação: total (colunas nullable que ela ignora; o
-- trigger só atua quando o snapshot já existe, o que a versão anterior nunca escreve).
-- Rollback (só se necessário; perde os snapshots já gravados — preferir forward-fix):
--   DROP TRIGGER trg_orders_delivery_address_immutable ON orders;
--   DROP FUNCTION orders_delivery_address_immutable();
--   ALTER TABLE orders DROP COLUMN delivery_address_id, DROP COLUMN delivery_recipient_name, ...;
-- Correção manual de um snapshot (ex.: ordem judicial/RGPD): desativar o trigger dentro da mesma
-- transação (ALTER TABLE orders DISABLE TRIGGER trg_orders_delivery_address_immutable), corrigir, reativar
-- e registar em audit_logs.

ALTER TABLE orders
    ADD COLUMN delivery_address_id UUID,
    ADD COLUMN delivery_recipient_name VARCHAR(160),
    ADD COLUMN delivery_phone VARCHAR(40),
    ADD COLUMN delivery_line1 VARCHAR(200),
    ADD COLUMN delivery_line2 VARCHAR(200),
    ADD COLUMN delivery_city VARCHAR(100),
    ADD COLUMN delivery_state VARCHAR(80),
    ADD COLUMN delivery_postal_code VARCHAR(30),
    ADD COLUMN delivery_latitude DOUBLE PRECISION,
    ADD COLUMN delivery_longitude DOUBLE PRECISION,
    ADD COLUMN delivery_instructions TEXT;

-- Uma vez gravado, o snapshot não muda (a aplicação também o mapeia com updatable = false).
CREATE OR REPLACE FUNCTION orders_delivery_address_immutable() RETURNS trigger AS $$
BEGIN
    IF OLD.delivery_line1 IS NOT NULL AND (
           NEW.delivery_address_id IS DISTINCT FROM OLD.delivery_address_id
        OR NEW.delivery_recipient_name IS DISTINCT FROM OLD.delivery_recipient_name
        OR NEW.delivery_phone IS DISTINCT FROM OLD.delivery_phone
        OR NEW.delivery_line1 IS DISTINCT FROM OLD.delivery_line1
        OR NEW.delivery_line2 IS DISTINCT FROM OLD.delivery_line2
        OR NEW.delivery_city IS DISTINCT FROM OLD.delivery_city
        OR NEW.delivery_state IS DISTINCT FROM OLD.delivery_state
        OR NEW.delivery_postal_code IS DISTINCT FROM OLD.delivery_postal_code
        OR NEW.delivery_latitude IS DISTINCT FROM OLD.delivery_latitude
        OR NEW.delivery_longitude IS DISTINCT FROM OLD.delivery_longitude
        OR NEW.delivery_instructions IS DISTINCT FROM OLD.delivery_instructions) THEN
        RAISE EXCEPTION 'order delivery address snapshot is immutable (order %)', OLD.id
            USING ERRCODE = 'check_violation';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_orders_delivery_address_immutable
    BEFORE UPDATE ON orders
    FOR EACH ROW EXECUTE FUNCTION orders_delivery_address_immutable();

COMMENT ON COLUMN orders.delivery_address_id IS 'Source customer_addresses.id at checkout (traceability only, no FK); snapshot columns delivery_* are immutable';
