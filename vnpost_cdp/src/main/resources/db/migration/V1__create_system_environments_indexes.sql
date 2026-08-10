CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX IF NOT EXISTS idx_system_environments_environment_trgm
ON system_environments USING gin (environment gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_system_environments_base_url_trgm
ON system_environments USING gin (base_url gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_system_environments_gateway_url_trgm
ON system_environments USING gin (gateway_url gin_trgm_ops);
