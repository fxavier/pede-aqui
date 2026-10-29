-- LAC-27: encerramentos excecionais de loja (feriados, inventário, avaria), consultados pelo
-- StoreAvailabilityService juntamente com vendor_opening_hours (V006). Um encerramento cobre o
-- intervalo semiaberto [starts_at, ends_at).
--
-- Locking: CREATE TABLE nova + índice sobre a tabela vazia. As FKs tomam SHARE ROW EXCLUSIVE sobre
--          tenants e vendors só durante o CREATE (instantâneo; não há linhas a validar). Não altera
--          tabelas existentes.
-- Compatibilidade: aditiva. A versão anterior da aplicação ignora a tabela; a nova trata "sem linhas"
--          como "sem encerramentos", logo nenhuma loja muda de estado com a migração.
-- Rollback: DROP TABLE vendor_closures; (nenhuma outra tabela a referencia; forward-fix preferido).
-- Sem UNIQUE nem índices parciais: não há validação de duplicados a fazer (encerramentos sobrepostos
-- são permitidos — a loja está fechada se QUALQUER encerramento cobrir o instante).

CREATE TABLE vendor_closures (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    vendor_id UUID NOT NULL REFERENCES vendors(id) ON DELETE CASCADE,
    starts_at TIMESTAMPTZ NOT NULL,
    ends_at TIMESTAMPTZ NOT NULL,
    reason VARCHAR(255) NOT NULL,
    created_by VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_vendor_closures_period CHECK (ends_at > starts_at)
);

-- Consultas: "encerramentos ainda não terminados destes vendors" (carrinho, checkout, pesquisa, listagem).
CREATE INDEX idx_vendor_closures_vendor_ends ON vendor_closures (vendor_id, ends_at);
