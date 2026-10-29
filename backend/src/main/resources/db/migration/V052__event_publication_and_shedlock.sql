-- W2-A (LAC-46, LAC-47, ADR-0002): infraestrutura de jobs e eventos transacionais.
--
-- 1. event_publication: registo de publicação de eventos do Spring Modulith (JDBC, spring-modulith-events-jdbc
--    1.4.x). Cada evento publicado dentro de uma transação grava uma linha por listener @ApplicationModuleListener
--    na MESMA transação de negócio (outbox); completion_date é preenchido quando o listener termina com sucesso.
--    Linhas sem completion_date = publicações incompletas, reenviadas pelo EventPublicationRetryJob.
--    Esquema idêntico a schema-postgresql.sql do módulo (a inicialização automática do Modulith está desligada:
--    spring.modulith.events.jdbc.schema-initialization.enabled=false; o Flyway é o único dono do esquema).
-- 2. shedlock: tabela do ShedLock (provider JdbcTemplate, usingDbTime) — um job agendado corre numa só réplica
--    de cada vez. name = nome do job; lock_until/locked_at em hora do servidor de BD (UTC).
--
-- Locking: só CREATE TABLE/INDEX de tabelas novas e vazias — nenhum lock em tabelas existentes, sem rewrite.
-- Compatibilidade com a versão anterior da aplicação: total. A versão anterior não conhece as tabelas e ignora-as;
--   a nova versão só escreve nelas. Um rollback da aplicação deixa as tabelas órfãs mas inofensivas (publicações
--   incompletas deixadas por um listener que já não existe nunca são entregues; podem ser apagadas à mão).
-- Rollback / forward-fix: Flyway community não reverte. Para remover numa migração nova (forward-fix):
--   DROP TABLE IF EXISTS event_publication; DROP TABLE IF EXISTS shedlock;
--   (perde as publicações incompletas — verificar antes: SELECT count(*) FROM event_publication WHERE completion_date IS NULL).

CREATE TABLE IF NOT EXISTS event_publication
(
    id               UUID                     NOT NULL,
    listener_id      TEXT                     NOT NULL,
    event_type       TEXT                     NOT NULL,
    serialized_event TEXT                     NOT NULL,
    publication_date TIMESTAMP WITH TIME ZONE NOT NULL,
    completion_date  TIMESTAMP WITH TIME ZONE,
    PRIMARY KEY (id)
);
-- Usado pelo Modulith para marcar a publicação concluída (lookup por evento serializado + listener).
CREATE INDEX IF NOT EXISTS event_publication_serialized_event_hash_idx ON event_publication USING hash (serialized_event);
-- Usado para listar incompletas (completion_date IS NULL) e para a limpeza de concluídas antigas.
CREATE INDEX IF NOT EXISTS event_publication_by_completion_date_idx ON event_publication (completion_date);

CREATE TABLE IF NOT EXISTS shedlock
(
    name       VARCHAR(64)  NOT NULL,
    lock_until TIMESTAMP    NOT NULL,
    locked_at  TIMESTAMP    NOT NULL,
    locked_by  VARCHAR(255) NOT NULL,
    PRIMARY KEY (name)
);
