BEGIN;

ALTER TABLE meta_group
    ADD COLUMN IF NOT EXISTS school_building_id BIGINT;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
          FROM pg_constraint
         WHERE conname = 'fk_meta_group_school_building'
           AND conrelid = 'meta_group'::regclass
           AND contype = 'f'
    ) THEN
        ALTER TABLE meta_group
            ADD CONSTRAINT fk_meta_group_school_building
            FOREIGN KEY (school_building_id)
            REFERENCES school_building(id)
            ON UPDATE RESTRICT
            ON DELETE RESTRICT;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_meta_group_school_building_id
    ON meta_group(school_building_id);

COMMIT;
