-- H2-compatible version (MODE=PostgreSQL) of admin-managed clients/scopes and deny rules

CREATE TABLE IF NOT EXISTS oauth2_client_metadata (
    client_id VARCHAR(100) PRIMARY KEY,
    is_system BOOLEAN NOT NULL DEFAULT FALSE,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    notes TEXT,
    created_by VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_client_metadata_client_id
        FOREIGN KEY (client_id) REFERENCES oauth2_registered_client(client_id)
);

CREATE INDEX IF NOT EXISTS idx_client_metadata_enabled ON oauth2_client_metadata(enabled);
CREATE INDEX IF NOT EXISTS idx_client_metadata_is_system ON oauth2_client_metadata(is_system);

CREATE TABLE IF NOT EXISTS oauth2_scope (
    scope VARCHAR(200) PRIMARY KEY,
    description TEXT,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    is_system BOOLEAN NOT NULL DEFAULT FALSE,
    created_by VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_oauth2_scope_enabled ON oauth2_scope(enabled);
CREATE INDEX IF NOT EXISTS idx_oauth2_scope_is_system ON oauth2_scope(is_system);

CREATE TABLE IF NOT EXISTS oauth2_client_scope (
    client_id VARCHAR(100) NOT NULL,
    scope VARCHAR(200) NOT NULL,
    created_by VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (client_id, scope),
    CONSTRAINT fk_client_scope_client_id
        FOREIGN KEY (client_id) REFERENCES oauth2_registered_client(client_id),
    CONSTRAINT fk_client_scope_scope
        FOREIGN KEY (scope) REFERENCES oauth2_scope(scope)
);

CREATE INDEX IF NOT EXISTS idx_client_scope_client_id ON oauth2_client_scope(client_id);
CREATE INDEX IF NOT EXISTS idx_client_scope_scope ON oauth2_client_scope(scope);

CREATE TABLE IF NOT EXISTS security_deny_rule (
    id BIGSERIAL PRIMARY KEY,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    provider VARCHAR(100),
    match_field VARCHAR(50) NOT NULL,
    match_type VARCHAR(20) NOT NULL,
    pattern TEXT NOT NULL,
    normalized_value VARCHAR(400),
    reason TEXT,
    created_by VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_deny_rule_enabled ON security_deny_rule(enabled);
CREATE INDEX IF NOT EXISTS idx_deny_rule_provider ON security_deny_rule(provider);
CREATE INDEX IF NOT EXISTS idx_deny_rule_match_field ON security_deny_rule(match_field);
CREATE INDEX IF NOT EXISTS idx_deny_rule_match_type ON security_deny_rule(match_type);
CREATE INDEX IF NOT EXISTS idx_deny_rule_normalized_value ON security_deny_rule(normalized_value);
