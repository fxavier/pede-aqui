-- INC-12 (AS-02, FR-003) — expand: pino GPS das moradas do cliente como geography(Point,4326).
--
-- Expand/contract: latitude/longitude continuam a ser a fonte escrita pela aplicação durante a
-- transição; a coluna `location` é derivada delas por trigger (qualquer escritor fica coerente, sem
-- dependência hibernate-spatial). O contract (deixar de escrever lat/lng, remover colunas e o índice
-- idx_customer_addresses_tenant_location de V004) fica para um incremento posterior.
--
-- Locking (tabela pequena: até INC-12 não havia API de moradas, pelo que está vazia ou quase):
--   * ADD COLUMN sem default / DROP NOT NULL: ACCESS EXCLUSIVE breve, só metadados.
--   * UPDATE de backfill: row locks nas linhas com coordenadas, dentro da transação da migração.
--   * CREATE INDEX ... USING GIST: lock SHARE (bloqueia escritas, não leituras) durante a construção.
--     Não se usa CONCURRENTLY porque o Flyway corre cada migração numa transação; com a tabela vazia
--     ou quase, a construção é instantânea. Se no futuro a tabela for grande, criar o índice fora do
--     Flyway com CREATE INDEX CONCURRENTLY.
--   * CREATE TRIGGER: SHARE ROW EXCLUSIVE breve.
-- Compatibilidade com a versão anterior da aplicação: total (coluna nullable não mapeada; state e
-- postal_code passam a aceitar NULL, a versão anterior não escrevia nesta tabela).
-- Rollback (só se necessário; preferir forward-fix):
--   DROP TRIGGER trg_customer_addresses_sync_location ON customer_addresses;
--   DROP FUNCTION customer_addresses_sync_location();
--   DROP INDEX idx_customer_addresses_location; DROP INDEX idx_customer_addresses_tenant_customer;
--   ALTER TABLE customer_addresses DROP COLUMN location;
--   (repor NOT NULL em state/postal_code só depois de preencher os NULL).
--
-- Validação antes/depois (coordenadas fora do intervalo ficam com location NULL e devem ser revistas):
--   SELECT id FROM customer_addresses
--    WHERE (latitude IS NULL) <> (longitude IS NULL)
--       OR latitude NOT BETWEEN -90 AND 90 OR longitude NOT BETWEEN -180 AND 180;

ALTER TABLE customer_addresses ADD COLUMN location geography(Point, 4326);

-- Moçambique não usa código postal de forma consistente; a província é opcional no formulário.
ALTER TABLE customer_addresses ALTER COLUMN state DROP NOT NULL;
ALTER TABLE customer_addresses ALTER COLUMN postal_code DROP NOT NULL;

-- Backfill: só pares completos e dentro do intervalo válido (o cast para geography rejeita o resto).
UPDATE customer_addresses
   SET location = ST_SetSRID(ST_MakePoint(longitude, latitude), 4326)::geography
 WHERE location IS NULL
   AND latitude IS NOT NULL AND longitude IS NOT NULL
   AND latitude BETWEEN -90 AND 90
   AND longitude BETWEEN -180 AND 180;

CREATE INDEX idx_customer_addresses_location ON customer_addresses USING GIST (location);
CREATE INDEX idx_customer_addresses_tenant_customer ON customer_addresses (tenant_id, customer_id);

-- Mantém location coerente com latitude/longitude enquanto ambas coexistem (fase expand).
CREATE OR REPLACE FUNCTION customer_addresses_sync_location() RETURNS trigger AS $$
BEGIN
    IF NEW.latitude IS NOT NULL AND NEW.longitude IS NOT NULL THEN
        NEW.location := ST_SetSRID(ST_MakePoint(NEW.longitude, NEW.latitude), 4326)::geography;
    ELSE
        NEW.location := NULL;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_customer_addresses_sync_location
    BEFORE INSERT OR UPDATE OF latitude, longitude ON customer_addresses
    FOR EACH ROW EXECUTE FUNCTION customer_addresses_sync_location();

COMMENT ON COLUMN customer_addresses.location IS 'GPS pin (WGS84); derived from latitude/longitude by trigger during the INC-12 expand phase';
