BEGIN;

ALTER TABLE curriculum_plan_entry
    ADD COLUMN IF NOT EXISTS modular_system boolean NOT NULL DEFAULT false;

CREATE TABLE IF NOT EXISTS curriculum_module (
    id bigserial PRIMARY KEY,
    curriculum_entry_id bigint NOT NULL REFERENCES curriculum_plan_entry(id) ON DELETE CASCADE,
    module_order integer NOT NULL,
    module_name varchar(255) NOT NULL,
    planned_hours numeric(10, 2) NOT NULL,
    subgroup_required boolean NOT NULL DEFAULT false,
    subgroup_count integer NOT NULL DEFAULT 0,
    education_level varchar(32) NOT NULL,
    subgroup1_hours integer,
    subgroup1_education_level varchar(32),
    subgroup2_hours integer,
    subgroup2_education_level varchar(32),
    CONSTRAINT uk_curriculum_module_order UNIQUE (curriculum_entry_id, module_order)
);

CREATE INDEX IF NOT EXISTS idx_curriculum_module_entry
    ON curriculum_module(curriculum_entry_id);

ALTER TABLE manual_load_entry
    ADD COLUMN IF NOT EXISTS curriculum_module_id bigint;

ALTER TABLE manual_load_entry
    DROP CONSTRAINT IF EXISTS fk_manual_load_curriculum_module;

ALTER TABLE manual_load_entry
    ADD CONSTRAINT fk_manual_load_curriculum_module
        FOREIGN KEY (curriculum_module_id) REFERENCES curriculum_module(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_manual_load_curriculum_module
    ON manual_load_entry(curriculum_module_id);

COMMIT;
