CREATE TABLE verticals (
    id          UUID         PRIMARY KEY,
    tenant_id   UUID         NOT NULL,
    slug        VARCHAR(100) NOT NULL,
    label       VARCHAR(255) NOT NULL,
    active      BOOLEAN      NOT NULL DEFAULT true,
    created_at  TIMESTAMPTZ  NOT NULL,
    updated_at  TIMESTAMPTZ  NOT NULL,
    UNIQUE (tenant_id, slug)
);
