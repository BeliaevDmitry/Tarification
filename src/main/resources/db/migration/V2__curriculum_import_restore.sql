ALTER TABLE curriculum_plan_entry
    ADD COLUMN IF NOT EXISTS academic_year VARCHAR(64) NOT NULL DEFAULT '',
    ADD COLUMN IF NOT EXISTS stage VARCHAR(32) NOT NULL DEFAULT '',
    ADD COLUMN IF NOT EXISTS study_period VARCHAR(16) NOT NULL DEFAULT 'YEAR',
    ADD COLUMN IF NOT EXISTS deprecated BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE manual_load_entry
    ADD COLUMN IF NOT EXISTS orphaned BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE IF NOT EXISTS subject_catalog_entry (
    id BIGSERIAL PRIMARY KEY,
    subject_name VARCHAR(255) NOT NULL UNIQUE,
    subject_type VARCHAR(64) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

DROP INDEX IF EXISTS uk_curriculum_class_subject_level;
CREATE UNIQUE INDEX IF NOT EXISTS uk_curriculum_import_key
    ON curriculum_plan_entry (academic_year, stage, class_name, subject_name, study_period);
