CREATE TABLE tenants (
    id UUID PRIMARY KEY,
    name VARCHAR(160) NOT NULL,
    slug VARCHAR(80) NOT NULL UNIQUE,
    status VARCHAR(30) NOT NULL,
    default_currency VARCHAR(3) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE app_user_profiles (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    keycloak_user_id VARCHAR(120) NOT NULL,
    email VARCHAR(200) NOT NULL,
    display_name VARCHAR(160) NOT NULL,
    phone VARCHAR(40),
    status VARCHAR(30) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_app_user_profiles_tenant_keycloak UNIQUE (tenant_id, keycloak_user_id)
);

CREATE TABLE app_user_profile_roles (
    app_user_profile_id UUID NOT NULL REFERENCES app_user_profiles(id) ON DELETE CASCADE,
    role VARCHAR(60) NOT NULL,
    PRIMARY KEY (app_user_profile_id, role)
);

CREATE INDEX idx_app_user_profiles_tenant ON app_user_profiles(tenant_id);
CREATE INDEX idx_app_user_profiles_email ON app_user_profiles(email);
