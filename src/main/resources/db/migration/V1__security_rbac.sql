CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(255) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    email VARCHAR(255),
    full_name VARCHAR(255),
    role VARCHAR(64) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS audit_log (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT,
    username VARCHAR(255),
    action VARCHAR(64) NOT NULL,
    entity_type VARCHAR(255),
    entity_id BIGINT,
    old_value TEXT,
    new_value TEXT,
    details TEXT,
    ip_address VARCHAR(255),
    timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_audit_user FOREIGN KEY (user_id) REFERENCES users(id)
);

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'school_building') THEN
        ALTER TABLE school_building ADD COLUMN IF NOT EXISTS head_user_id BIGINT;
        ALTER TABLE school_building DROP CONSTRAINT IF EXISTS uk_school_building_head_user;
        ALTER TABLE school_building ADD CONSTRAINT uk_school_building_head_user UNIQUE (head_user_id);
        ALTER TABLE school_building DROP CONSTRAINT IF EXISTS fk_school_building_head_user;
        ALTER TABLE school_building ADD CONSTRAINT fk_school_building_head_user FOREIGN KEY (head_user_id) REFERENCES users(id);
    END IF;
END $$;
