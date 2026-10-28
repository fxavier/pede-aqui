-- ADR-0001 (INC-11, AS-03, SEC-06): tenant = operador do marketplace.
-- Cria o tenant do operador Pede-Aqui com id fixo, referido por app.tenancy.operator-tenant-id e pelo
-- mapa client OIDC -> tenant dos clientes (CUSTOMER). Também é o tenant dos utilizadores seed
-- ops/finance/support do realm de dev (keycloak/delivery-realm.json).
-- Idempotente e sem efeito em dados existentes: se o id ou o slug já existirem, não faz nada
-- (o arranque valida que o tenant configurado existe).
INSERT INTO tenants (id, name, slug, status, default_currency, created_at, updated_at)
VALUES ('5e6d0c1a-7b2f-4f3e-9c8a-0d1e2f3a4b5c', 'Pede-Aqui', 'pede-aqui-operador', 'ACTIVE', 'MZN', NOW(), NOW())
ON CONFLICT DO NOTHING;
