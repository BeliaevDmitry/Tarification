BEGIN;

CREATE TABLE IF NOT EXISTS student_profile (
    id BIGSERIAL PRIMARY KEY,
    current_full_name VARCHAR(255) NOT NULL,
    normalized_full_name VARCHAR(255) NOT NULL,
    birth_date DATE,
    record_number VARCHAR(255),
    normalized_record_number VARCHAR(255),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    first_seen_date DATE,
    last_seen_date DATE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_student_profile_record
    ON student_profile (normalized_record_number);
CREATE INDEX IF NOT EXISTS idx_student_profile_name_birth
    ON student_profile (normalized_full_name, birth_date);

ALTER TABLE contingent_student
    ADD COLUMN IF NOT EXISTS student_id BIGINT,
    ADD COLUMN IF NOT EXISTS identity_match_status VARCHAR(48) NOT NULL DEFAULT 'PENDING';

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_contingent_student_profile') THEN
        ALTER TABLE contingent_student
            ADD CONSTRAINT fk_contingent_student_profile
            FOREIGN KEY (student_id) REFERENCES student_profile(id) ON DELETE SET NULL;
    END IF;
END
$$;

CREATE INDEX IF NOT EXISTS idx_contingent_student_profile
    ON contingent_student (student_id);

CREATE TABLE IF NOT EXISTS student_name_history (
    id BIGSERIAL PRIMARY KEY,
    student_id BIGINT NOT NULL REFERENCES student_profile(id) ON DELETE CASCADE,
    full_name VARCHAR(255) NOT NULL,
    normalized_full_name VARCHAR(255) NOT NULL,
    valid_from DATE,
    valid_to DATE,
    created_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_student_name_history_student
    ON student_name_history (student_id);

CREATE TABLE IF NOT EXISTS student_class_enrollment (
    id BIGSERIAL PRIMARY KEY,
    student_id BIGINT NOT NULL REFERENCES student_profile(id) ON DELETE CASCADE,
    class_id BIGINT REFERENCES classroom_leadership_entry(id) ON DELETE SET NULL,
    academic_year VARCHAR(32) NOT NULL,
    class_name VARCHAR(255) NOT NULL,
    valid_from DATE,
    valid_to DATE,
    status VARCHAR(32) NOT NULL,
    source_snapshot_id BIGINT REFERENCES contingent_snapshot(id) ON DELETE SET NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_student_enrollment_student_year
    ON student_class_enrollment (student_id, academic_year);
CREATE INDEX IF NOT EXISTS idx_student_enrollment_class_year
    ON student_class_enrollment (class_id, academic_year);

CREATE TABLE IF NOT EXISTS student_support_status (
    id BIGSERIAL PRIMARY KEY,
    student_id BIGINT NOT NULL REFERENCES student_profile(id) ON DELETE CASCADE,
    academic_year VARCHAR(32) NOT NULL,
    category VARCHAR(16) NOT NULL,
    nosology_id BIGINT,
    nosology_code_snapshot VARCHAR(255),
    aoop_variant_snapshot VARCHAR(255),
    valid_from DATE NOT NULL,
    valid_to DATE,
    comment VARCHAR(1000),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_student_support_status_student_year
    ON student_support_status (student_id, academic_year);
CREATE INDEX IF NOT EXISTS idx_student_support_status_dates
    ON student_support_status (valid_from, valid_to);

CREATE TABLE IF NOT EXISTS nosology_catalog_entry (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(255) NOT NULL,
    name VARCHAR(255) NOT NULL,
    ovz_category VARCHAR(255),
    aoop_variant VARCHAR(255),
    student_category VARCHAR(16) NOT NULL,
    valid_from DATE,
    valid_to DATE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    comment VARCHAR(1000),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_nosology_catalog_code UNIQUE (code)
);

ALTER TABLE student_support_status
    ADD COLUMN IF NOT EXISTS nosology_id BIGINT,
    ADD COLUMN IF NOT EXISTS nosology_code_snapshot VARCHAR(255),
    ADD COLUMN IF NOT EXISTS aoop_variant_snapshot VARCHAR(255);

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_student_support_status_nosology') THEN
        ALTER TABLE student_support_status
            ADD CONSTRAINT fk_student_support_status_nosology
            FOREIGN KEY (nosology_id) REFERENCES nosology_catalog_entry(id) ON DELETE SET NULL;
    END IF;
END
$$;

CREATE TABLE IF NOT EXISTS iup_plan (
    id BIGSERIAL PRIMARY KEY,
    student_id BIGINT NOT NULL REFERENCES student_profile(id) ON DELETE CASCADE,
    academic_year VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    order_number VARCHAR(255),
    order_date DATE,
    valid_from DATE NOT NULL,
    valid_to DATE,
    version_number INTEGER NOT NULL DEFAULT 1,
    comment VARCHAR(1000),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_iup_plan_student_year
    ON iup_plan (student_id, academic_year);
CREATE INDEX IF NOT EXISTS idx_iup_plan_dates
    ON iup_plan (valid_from, valid_to);

CREATE TABLE IF NOT EXISTS iup_subject_line (
    id BIGSERIAL PRIMARY KEY,
    iup_plan_id BIGINT NOT NULL REFERENCES iup_plan(id) ON DELETE CASCADE,
    subject_name VARCHAR(255) NOT NULL,
    curriculum_entry_id BIGINT REFERENCES curriculum_plan_entry(id) ON DELETE SET NULL,
    participation_mode VARCHAR(32) NOT NULL,
    class_hours NUMERIC(10, 2) NOT NULL DEFAULT 0,
    individual_hours NUMERIC(10, 2) NOT NULL DEFAULT 0,
    group_name_educational_plan VARCHAR(255),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_iup_subject_line_plan
    ON iup_subject_line (iup_plan_id);

CREATE TABLE IF NOT EXISTS iup_teacher_assignment (
    id BIGSERIAL PRIMARY KEY,
    iup_subject_line_id BIGINT NOT NULL REFERENCES iup_subject_line(id) ON DELETE CASCADE,
    teacher_id BIGINT REFERENCES teacher_directory_entry(id) ON DELETE SET NULL,
    teacher_fio_snapshot VARCHAR(255) NOT NULL,
    hours_per_week NUMERIC(10, 2) NOT NULL,
    delivery_form VARCHAR(32) NOT NULL,
    valid_from DATE NOT NULL,
    valid_to DATE,
    created_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_iup_teacher_assignment_line
    ON iup_teacher_assignment (iup_subject_line_id);
CREATE INDEX IF NOT EXISTS idx_iup_teacher_assignment_teacher
    ON iup_teacher_assignment (teacher_id);

CREATE TABLE IF NOT EXISTS student_group_membership (
    id BIGSERIAL PRIMARY KEY,
    student_id BIGINT NOT NULL REFERENCES student_profile(id) ON DELETE CASCADE,
    academic_year VARCHAR(32) NOT NULL,
    curriculum_entry_id BIGINT REFERENCES curriculum_plan_entry(id) ON DELETE CASCADE,
    meta_group_id BIGINT REFERENCES meta_group(id) ON DELETE CASCADE,
    group_name_educational_plan VARCHAR(255) NOT NULL,
    valid_from DATE NOT NULL,
    valid_to DATE,
    source VARCHAR(32) NOT NULL,
    iup_subject_line_id BIGINT REFERENCES iup_subject_line(id) ON DELETE CASCADE,
    source_batch_id BIGINT,
    created_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_student_group_membership_student
    ON student_group_membership (student_id, academic_year);
CREATE INDEX IF NOT EXISTS idx_student_group_membership_scope
    ON student_group_membership (curriculum_entry_id, meta_group_id);

CREATE TABLE IF NOT EXISTS curriculum_mesh_mapping (
    id BIGSERIAL PRIMARY KEY,
    academic_year VARCHAR(32) NOT NULL,
    curriculum_entry_id BIGINT NOT NULL REFERENCES curriculum_plan_entry(id) ON DELETE CASCADE,
    subject_name_up VARCHAR(255) NOT NULL,
    class_name_up VARCHAR(255) NOT NULL,
    group_name_up VARCHAR(255) NOT NULL DEFAULT '',
    subject_name_mesh VARCHAR(255) NOT NULL,
    class_name_mesh VARCHAR(255) NOT NULL,
    group_name_mesh VARCHAR(255) NOT NULL DEFAULT '',
    confirmed BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_curriculum_mesh_mapping_scope
        UNIQUE (academic_year, curriculum_entry_id, group_name_up)
);

CREATE INDEX IF NOT EXISTS idx_curriculum_mesh_mapping_year
    ON curriculum_mesh_mapping (academic_year);
CREATE INDEX IF NOT EXISTS idx_curriculum_mesh_mapping_entry
    ON curriculum_mesh_mapping (curriculum_entry_id);

ALTER TABLE manual_load_entry
    ADD COLUMN IF NOT EXISTS load_source VARCHAR(16) NOT NULL DEFAULT 'CORE',
    ADD COLUMN IF NOT EXISTS precise_load_hours NUMERIC(10, 2),
    ADD COLUMN IF NOT EXISTS source_iup_plan_id BIGINT,
    ADD COLUMN IF NOT EXISTS source_iup_assignment_id BIGINT,
    ADD COLUMN IF NOT EXISTS source_student_id BIGINT,
    ADD COLUMN IF NOT EXISTS iup_student_category VARCHAR(16);

UPDATE manual_load_entry
SET load_source = 'CORE'
WHERE load_source IS NULL;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_manual_load_iup_plan') THEN
        ALTER TABLE manual_load_entry
            ADD CONSTRAINT fk_manual_load_iup_plan
            FOREIGN KEY (source_iup_plan_id) REFERENCES iup_plan(id) ON DELETE CASCADE;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_manual_load_iup_assignment') THEN
        ALTER TABLE manual_load_entry
            ADD CONSTRAINT fk_manual_load_iup_assignment
            FOREIGN KEY (source_iup_assignment_id) REFERENCES iup_teacher_assignment(id) ON DELETE CASCADE;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_manual_load_iup_student') THEN
        ALTER TABLE manual_load_entry
            ADD CONSTRAINT fk_manual_load_iup_student
            FOREIGN KEY (source_student_id) REFERENCES student_profile(id) ON DELETE CASCADE;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uk_manual_load_iup_assignment') THEN
        ALTER TABLE manual_load_entry
            ADD CONSTRAINT uk_manual_load_iup_assignment UNIQUE (source_iup_assignment_id);
    END IF;
END
$$;

CREATE INDEX IF NOT EXISTS idx_manual_load_source_year
    ON manual_load_entry (academic_year, load_source);

CREATE TABLE IF NOT EXISTS student_support_document (
    id BIGSERIAL PRIMARY KEY,
    student_id BIGINT NOT NULL REFERENCES student_profile(id),
    academic_year VARCHAR(20) NOT NULL,
    document_type VARCHAR(40) NOT NULL,
    accepted_form VARCHAR(30) NOT NULL DEFAULT 'COPY',
    document_number VARCHAR(255),
    issue_date DATE,
    valid_from DATE,
    valid_to DATE,
    issuing_organization VARCHAR(500),
    received_at DATE NOT NULL,
    responsible_employee VARCHAR(255),
    comment VARCHAR(2000),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_support_document_student_year
    ON student_support_document (student_id, academic_year);
CREATE INDEX IF NOT EXISTS idx_support_document_dates
    ON student_support_document (valid_from, valid_to);
CREATE INDEX IF NOT EXISTS idx_support_document_type
    ON student_support_document (document_type);

CREATE TABLE IF NOT EXISTS student_support_document_attachment (
    id BIGSERIAL PRIMARY KEY,
    document_id BIGINT NOT NULL REFERENCES student_support_document(id) ON DELETE CASCADE,
    original_file_name VARCHAR(500) NOT NULL,
    content_type VARCHAR(150) NOT NULL,
    file_size BIGINT NOT NULL,
    content BYTEA NOT NULL,
    uploaded_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    uploaded_by VARCHAR(255) NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_support_document_attachment_document
    ON student_support_document_attachment (document_id);

ALTER TABLE student_support_document
    ADD COLUMN IF NOT EXISTS nosology_code VARCHAR(16),
    ADD COLUMN IF NOT EXISTS education_stage VARCHAR(16),
    ADD COLUMN IF NOT EXISTS education_program VARCHAR(2000),
    ADD COLUMN IF NOT EXISTS prolongation_available BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS prolongation_used BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS prolonged_grade INTEGER,
    ADD COLUMN IF NOT EXISTS prolonged_academic_year VARCHAR(16),
    ADD COLUMN IF NOT EXISTS ipra_present BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE student_support_document
    ALTER COLUMN education_program TYPE VARCHAR(2000) USING education_program::text;

UPDATE student_support_document
SET education_program = CASE education_program
    WHEN 'DEAF' THEN 'Глухие'
    WHEN 'HARD_OF_HEARING' THEN 'Слабослышащие, позднооглохшие, кохлеарно имплантированные, глухие'
    WHEN 'BLIND' THEN 'Слепые'
    WHEN 'VISUALLY_IMPAIRED' THEN 'Слабовидящие'
    WHEN 'TNR' THEN 'ТНР'
    WHEN 'NODA' THEN 'НОДА'
    WHEN 'ZPR' THEN 'ЗПР'
    WHEN 'RAS' THEN 'РАС'
    WHEN 'UO' THEN 'УО'
    ELSE education_program
END
WHERE education_program IN ('DEAF', 'HARD_OF_HEARING', 'BLIND', 'VISUALLY_IMPAIRED',
                            'TNR', 'NODA', 'ZPR', 'RAS', 'UO');

UPDATE student_support_document
SET accepted_form = 'COPY'
WHERE document_type = 'MSE_CERTIFICATE'
  AND accepted_form <> 'COPY';

ALTER TABLE student_support_document ALTER COLUMN received_at DROP NOT NULL;

ALTER TABLE student_support_status
    ADD COLUMN IF NOT EXISTS source_document_id BIGINT;

UPDATE student_support_status status
SET category = 'K2',
    nosology_id = NULL,
    nosology_code_snapshot = NULL,
    updated_at = CURRENT_TIMESTAMP
WHERE status.source_document_id IN (
    SELECT document.id
    FROM student_support_document document
    WHERE document.document_type = 'MSE_CERTIFICATE'
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_support_status_source_document
    ON student_support_status (source_document_id)
    WHERE source_document_id IS NOT NULL;

CREATE TABLE IF NOT EXISTS correction_specialist_catalog_entry (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    built_in BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_correction_specialist_name UNIQUE (name)
);

CREATE TABLE IF NOT EXISTS student_support_document_correction (
    id BIGSERIAL PRIMARY KEY,
    document_id BIGINT NOT NULL REFERENCES student_support_document(id) ON DELETE CASCADE,
    specialist_id BIGINT NOT NULL REFERENCES correction_specialist_catalog_entry(id),
    tasks VARCHAR(4000) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_support_document_correction UNIQUE (document_id, specialist_id)
);

CREATE INDEX IF NOT EXISTS idx_support_document_correction_document
    ON student_support_document_correction (document_id);

CREATE INDEX IF NOT EXISTS idx_manual_load_iup_plan
    ON manual_load_entry (source_iup_plan_id);
CREATE INDEX IF NOT EXISTS idx_manual_load_iup_student
    ON manual_load_entry (source_student_id, academic_year);

COMMIT;
