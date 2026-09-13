BEGIN;

CREATE TABLE IF NOT EXISTS teacher_time_off_entry (
    id BIGSERIAL PRIMARY KEY,
    teacher_id BIGINT NOT NULL,
    teacher_fio_snapshot VARCHAR(255) NOT NULL,
    operation_type VARCHAR(16) NOT NULL,
    amount_minutes INTEGER NOT NULL CHECK (amount_minutes > 0 AND MOD(amount_minutes, 5) = 0),
    reason VARCHAR(2000),
    lesson_removal BOOLEAN,
    event_date DATE NOT NULL,
    created_by_user_id BIGINT,
    created_by_username VARCHAR(100) NOT NULL,
    created_by_fio VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT chk_teacher_time_off_operation CHECK (operation_type IN ('EARNED', 'USED'))
);

CREATE INDEX IF NOT EXISTS idx_teacher_time_off_teacher
    ON teacher_time_off_entry (teacher_id);
CREATE INDEX IF NOT EXISTS idx_teacher_time_off_event_date
    ON teacher_time_off_entry (event_date);
CREATE INDEX IF NOT EXISTS idx_teacher_time_off_operation
    ON teacher_time_off_entry (operation_type);

COMMIT;
