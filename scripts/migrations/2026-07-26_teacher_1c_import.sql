BEGIN;

ALTER TABLE teacher_directory_entry
    ADD COLUMN IF NOT EXISTS primary_position VARCHAR(255),
    ADD COLUMN IF NOT EXISTS personnel_number VARCHAR(64),
    ADD COLUMN IF NOT EXISTS employment_type VARCHAR(128),
    ADD COLUMN IF NOT EXISTS employment_date DATE,
    ADD COLUMN IF NOT EXISTS last_one_c_sync_at TIMESTAMP;

ALTER TABLE app_user
    ADD COLUMN IF NOT EXISTS teacher_id BIGINT;

UPDATE app_user u
   SET teacher_id = t.id
  FROM teacher_directory_entry t
 WHERE u.teacher_id IS NULL
   AND lower(trim(u.full_name)) = lower(trim(t.fio_teacher));

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
          FROM pg_constraint
         WHERE conname = 'fk_app_user_teacher'
    ) THEN
        ALTER TABLE app_user
            ADD CONSTRAINT fk_app_user_teacher
            FOREIGN KEY (teacher_id)
            REFERENCES teacher_directory_entry(id)
            ON UPDATE RESTRICT
            ON DELETE SET NULL;
    END IF;
END
$$;

CREATE UNIQUE INDEX IF NOT EXISTS uk_app_user_teacher_id
    ON app_user (teacher_id)
    WHERE teacher_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_teacher_directory_personnel_number
    ON teacher_directory_entry (personnel_number);

COMMIT;
