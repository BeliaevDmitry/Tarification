BEGIN;

ALTER TABLE manual_load_entry
    ADD COLUMN IF NOT EXISTS curriculum_part varchar(32);

UPDATE manual_load_entry mle
SET curriculum_part = (
    SELECT min(cpe.curriculum_part)
    FROM curriculum_plan_entry cpe
    WHERE cpe.academic_year = mle.academic_year
      AND cpe.subject_id = mle.subject_id
      AND (cpe.class_id = mle.class_id OR cpe.meta_group_id = mle.meta_group_id)
      AND cpe.education_level = mle.education_level
      AND coalesce(cpe.study_period, 'YEAR') = coalesce(mle.study_period, 'YEAR')
      AND coalesce(cpe.deprecated, false) = false
    HAVING count(DISTINCT cpe.curriculum_part) = 1
)
WHERE mle.curriculum_part IS NULL;

UPDATE manual_load_entry
SET curriculum_part = 'CORE'
WHERE curriculum_part IS NULL;

ALTER TABLE manual_load_entry
    ALTER COLUMN curriculum_part SET DEFAULT 'CORE',
    ALTER COLUMN curriculum_part SET NOT NULL;

COMMIT;
