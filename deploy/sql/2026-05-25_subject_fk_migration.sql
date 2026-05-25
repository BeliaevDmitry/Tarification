-- Subject FK migration for curriculum and manual load
-- Run on PostgreSQL in a maintenance window.

BEGIN;

-- 1) Add new FK columns (nullable for safe rollout)
ALTER TABLE curriculum_plan_entry ADD COLUMN IF NOT EXISTS subject_id BIGINT;
ALTER TABLE manual_load_entry ADD COLUMN IF NOT EXISTS subject_id BIGINT;

-- 2) Backfill by case-insensitive subject name
UPDATE curriculum_plan_entry c
SET subject_id = s.id,
    subjectName = s.subjectName
FROM subject_catalog_entry s
WHERE c.subject_id IS NULL
  AND lower(c.subjectName) = lower(s.subjectName);

UPDATE manual_load_entry m
SET subject_id = s.id,
    subjectName = s.subjectName
FROM subject_catalog_entry s
WHERE m.subject_id IS NULL
  AND lower(m.subjectName) = lower(s.subjectName);

-- 3) Create indexes for FK columns
CREATE INDEX IF NOT EXISTS idx_curriculum_plan_entry_subject_id ON curriculum_plan_entry(subject_id);
CREATE INDEX IF NOT EXISTS idx_manual_load_entry_subject_id ON manual_load_entry(subject_id);

-- 4) Add FK constraints
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE constraint_name = 'fk_curriculum_plan_entry_subject'
          AND table_name = 'curriculum_plan_entry'
    ) THEN
        ALTER TABLE curriculum_plan_entry
            ADD CONSTRAINT fk_curriculum_plan_entry_subject
            FOREIGN KEY (subject_id) REFERENCES subject_catalog_entry(id)
            ON UPDATE RESTRICT ON DELETE RESTRICT;
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE constraint_name = 'fk_manual_load_entry_subject'
          AND table_name = 'manual_load_entry'
    ) THEN
        ALTER TABLE manual_load_entry
            ADD CONSTRAINT fk_manual_load_entry_subject
            FOREIGN KEY (subject_id) REFERENCES subject_catalog_entry(id)
            ON UPDATE RESTRICT ON DELETE RESTRICT;
    END IF;
END $$;

COMMIT;

-- 5) Post-check queries (run manually)
-- SELECT COUNT(*) AS curriculum_unmapped FROM curriculum_plan_entry WHERE subject_id IS NULL;
-- SELECT COUNT(*) AS manual_load_unmapped FROM manual_load_entry WHERE subject_id IS NULL;
