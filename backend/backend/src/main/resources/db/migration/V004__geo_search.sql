CREATE INDEX idx_customer_addresses_tenant_location ON customer_addresses(tenant_id, latitude, longitude);
CREATE INDEX idx_products_tenant_category ON products(tenant_id, category_id);
