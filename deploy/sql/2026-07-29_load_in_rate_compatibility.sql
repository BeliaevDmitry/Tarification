BEGIN;

ALTER TABLE manual_load_entry
    ADD COLUMN IF NOT EXISTS employment_contract_id bigint;
ALTER TABLE manual_load_entry
    ADD COLUMN IF NOT EXISTS included_in_rate_hours numeric(10, 2);
ALTER TABLE manual_load_entry
    ADD COLUMN IF NOT EXISTS in_rate_allocation_confirmed boolean DEFAULT false;
UPDATE manual_load_entry
   SET in_rate_allocation_confirmed = false
 WHERE in_rate_allocation_confirmed IS NULL;
ALTER TABLE manual_load_entry
    ALTER COLUMN in_rate_allocation_confirmed SET DEFAULT false;
ALTER TABLE manual_load_entry
    ALTER COLUMN in_rate_allocation_confirmed SET NOT NULL;
ALTER TABLE manual_load_entry
    ADD COLUMN IF NOT EXISTS in_rate_reason varchar(1000);
ALTER TABLE manual_load_entry
    ADD COLUMN IF NOT EXISTS in_rate_updated_at timestamp;
ALTER TABLE manual_load_entry
    ADD COLUMN IF NOT EXISTS in_rate_updated_by varchar(255);
CREATE INDEX IF NOT EXISTS idx_manual_load_employment_contract
    ON manual_load_entry(employment_contract_id);

ALTER TABLE employment_contract
    ADD COLUMN IF NOT EXISTS load_hours_may_be_included_in_rate boolean DEFAULT false;
UPDATE employment_contract
   SET load_hours_may_be_included_in_rate = false
 WHERE load_hours_may_be_included_in_rate IS NULL;
ALTER TABLE employment_contract
    ALTER COLUMN load_hours_may_be_included_in_rate SET DEFAULT false;
ALTER TABLE employment_contract
    ALTER COLUMN load_hours_may_be_included_in_rate SET NOT NULL;
ALTER TABLE employment_contract
    ADD COLUMN IF NOT EXISTS load_in_rate_rule_id bigint;
ALTER TABLE employment_contract
    ADD COLUMN IF NOT EXISTS load_in_rate_document_label varchar(1000);

CREATE TABLE IF NOT EXISTS load_in_rate_rule (
    id bigserial PRIMARY KEY,
    name varchar(255) NOT NULL,
    document_label varchar(1000) NOT NULL,
    active boolean NOT NULL DEFAULT true,
    created_at timestamp NOT NULL DEFAULT now(),
    updated_at timestamp NOT NULL DEFAULT now(),
    CONSTRAINT uk_load_in_rate_rule_name UNIQUE (name)
);

CREATE TABLE IF NOT EXISTS load_in_rate_rule_band (
    id bigserial PRIMARY KEY,
    rule_id bigint NOT NULL,
    min_total_hours numeric(10, 2) NOT NULL DEFAULT 0,
    max_total_hours numeric(10, 2),
    suggested_included_hours numeric(10, 2) NOT NULL DEFAULT 0,
    rate_fraction numeric(5, 2)
);
CREATE INDEX IF NOT EXISTS idx_load_in_rate_rule_band_rule
    ON load_in_rate_rule_band(rule_id);

CREATE TABLE IF NOT EXISTS load_in_rate_rule_subject (
    id bigserial PRIMARY KEY,
    rule_id bigint NOT NULL,
    subject_id bigint NOT NULL,
    CONSTRAINT uk_load_in_rate_rule_subject UNIQUE (rule_id, subject_id),
    CONSTRAINT fk_load_in_rate_rule_subject_rule FOREIGN KEY (rule_id)
        REFERENCES load_in_rate_rule(id) ON DELETE CASCADE,
    CONSTRAINT fk_load_in_rate_rule_subject_subject FOREIGN KEY (subject_id)
        REFERENCES subject_catalog_entry(id)
);
CREATE INDEX IF NOT EXISTS idx_load_in_rate_rule_subject_rule
    ON load_in_rate_rule_subject(rule_id);
CREATE INDEX IF NOT EXISTS idx_load_in_rate_rule_subject_subject
    ON load_in_rate_rule_subject(subject_id);

ALTER TABLE mcko_subject_mapping
    ADD COLUMN IF NOT EXISTS ignored boolean DEFAULT false;
UPDATE mcko_subject_mapping
   SET ignored = false
 WHERE ignored IS NULL;
ALTER TABLE mcko_subject_mapping
    ALTER COLUMN ignored SET DEFAULT false;
ALTER TABLE mcko_subject_mapping
    ALTER COLUMN ignored SET NOT NULL;
ALTER TABLE mcko_subject_mapping
    ADD COLUMN IF NOT EXISTS created_at timestamp DEFAULT now();
UPDATE mcko_subject_mapping
   SET created_at = now()
 WHERE created_at IS NULL;
ALTER TABLE mcko_subject_mapping
    ALTER COLUMN created_at SET DEFAULT now();
ALTER TABLE mcko_subject_mapping
    ALTER COLUMN created_at SET NOT NULL;
ALTER TABLE mcko_subject_mapping
    ADD COLUMN IF NOT EXISTS grade_band varchar(32) DEFAULT 'ALL';
UPDATE mcko_subject_mapping
   SET grade_band = 'ALL'
 WHERE grade_band IS NULL OR btrim(grade_band) = '';
ALTER TABLE mcko_subject_mapping
    ALTER COLUMN grade_band SET DEFAULT 'ALL';
ALTER TABLE mcko_subject_mapping
    ALTER COLUMN grade_band SET NOT NULL;
ALTER TABLE mcko_subject_mapping
    DROP CONSTRAINT IF EXISTS uk_mcko_subject_mapping;
ALTER TABLE mcko_subject_mapping
    ADD CONSTRAINT uk_mcko_subject_mapping
    UNIQUE (mcko_subject, subject_id, grade_band);

ALTER TABLE pa_specification
    ADD COLUMN IF NOT EXISTS grading_scale varchar(20) DEFAULT 'FIVE_POINT';
ALTER TABLE pa_specification
    ADD COLUMN IF NOT EXISTS pass_percent integer;
UPDATE pa_specification
   SET grading_scale = 'FIVE_POINT'
 WHERE grading_scale IS NULL OR btrim(grading_scale) = '';
ALTER TABLE pa_specification
    ALTER COLUMN grading_scale SET DEFAULT 'FIVE_POINT';
ALTER TABLE pa_specification
    ALTER COLUMN grading_scale SET NOT NULL;

COMMIT;
