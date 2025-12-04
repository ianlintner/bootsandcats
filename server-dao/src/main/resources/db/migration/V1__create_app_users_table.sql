-- Create app_users table for federated identity users
CREATE TABLE IF NOT EXISTS app_users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(255),
    email VARCHAR(255),
    provider VARCHAR(100) NOT NULL,
    provider_id VARCHAR(255) NOT NULL,
    name VARCHAR(255),
    picture_url TEXT,
    last_login TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uq_provider_provider_id UNIQUE (provider, provider_id)
);

CREATE INDEX IF NOT EXISTS idx_app_users_email ON app_users(email);
CREATE INDEX IF NOT EXISTS idx_app_users_username ON app_users(username);
