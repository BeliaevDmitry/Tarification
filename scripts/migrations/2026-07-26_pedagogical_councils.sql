BEGIN;

CREATE TABLE IF NOT EXISTS pedagogical_council_protocol (
    id BIGSERIAL PRIMARY KEY,
    academic_year VARCHAR(9) NOT NULL,
    protocol_number VARCHAR(64) NOT NULL,
    meeting_date DATE NOT NULL,
    agenda_time TIME,
    status VARCHAR(24) NOT NULL,
    source_type VARCHAR(24) NOT NULL,
    school_code_snapshot VARCHAR(32) NOT NULL,
    school_name_snapshot VARCHAR(512) NOT NULL,
    attendee_count INTEGER NOT NULL DEFAULT 0,
    chair_teacher_id BIGINT,
    chair_position_snapshot VARCHAR(255),
    chair_fio_snapshot VARCHAR(255),
    secretary_teacher_id BIGINT,
    secretary_position_snapshot VARCHAR(255),
    secretary_fio_snapshot VARCHAR(255),
    archive_filename VARCHAR(512),
    archive_document BYTEA,
    created_by_username VARCHAR(100) NOT NULL,
    created_by_fio VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    registered_at TIMESTAMP,
    registered_by VARCHAR(255),
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_ped_council_year_date
    ON pedagogical_council_protocol (academic_year, meeting_date);
CREATE INDEX IF NOT EXISTS idx_ped_council_status
    ON pedagogical_council_protocol (status);

CREATE TABLE IF NOT EXISTS pedagogical_council_item (
    id BIGSERIAL PRIMARY KEY,
    protocol_id BIGINT NOT NULL REFERENCES pedagogical_council_protocol(id) ON DELETE CASCADE,
    item_order INTEGER NOT NULL,
    agenda_title VARCHAR(2000) NOT NULL,
    agenda_time TIME,
    agenda_duration_minutes INTEGER DEFAULT 10,
    speaker_teacher_id BIGINT,
    speaker_position_snapshot VARCHAR(255),
    speaker_fio_snapshot VARCHAR(255),
    speech_content TEXT,
    decision_text TEXT NOT NULL,
    votes_for INTEGER NOT NULL DEFAULT 0,
    votes_against INTEGER NOT NULL DEFAULT 0,
    votes_abstained INTEGER NOT NULL DEFAULT 0
);

ALTER TABLE pedagogical_council_item
    ADD COLUMN IF NOT EXISTS agenda_duration_minutes INTEGER;
UPDATE pedagogical_council_item
   SET agenda_duration_minutes = 10
 WHERE agenda_duration_minutes IS NULL;
ALTER TABLE pedagogical_council_item
    ALTER COLUMN agenda_duration_minutes SET DEFAULT 10;

CREATE INDEX IF NOT EXISTS idx_ped_council_item_protocol
    ON pedagogical_council_item (protocol_id, item_order);

CREATE TABLE IF NOT EXISTS pedagogical_council_attachment (
    id BIGSERIAL PRIMARY KEY,
    item_id BIGINT NOT NULL REFERENCES pedagogical_council_item(id) ON DELETE CASCADE,
    attachment_number INTEGER NOT NULL,
    original_filename VARCHAR(512) NOT NULL,
    content BYTEA NOT NULL,
    uploaded_by VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_ped_council_attachment_item
    ON pedagogical_council_attachment (item_id, attachment_number);

COMMIT;
