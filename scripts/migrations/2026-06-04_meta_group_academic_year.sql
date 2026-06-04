BEGIN;

ALTER TABLE meta_group
    ADD COLUMN IF NOT EXISTS academic_year VARCHAR(9);

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
          FROM meta_group mg
         WHERE NOT EXISTS (
                   SELECT 1
                     FROM curriculum_plan_entry cpe
                    WHERE cpe.meta_group_id = mg.id
               )
           AND NOT EXISTS (
                   SELECT 1
                     FROM manual_load_entry mle
                    WHERE mle.meta_group_id = mg.id
               )
    ) THEN
        RAISE EXCEPTION 'Cannot assign meta_group.academic_year: unused meta groups exist. Remove or assign them manually before migration.';
    END IF;
END $$;

-- The legacy unique scope does not include academic_year, so historical copies would
-- violate it while the migration is splitting multi-year rows. The new year-scoped
-- constraint is added after all rows have a non-null academic_year.
ALTER TABLE meta_group
    DROP CONSTRAINT IF EXISTS uk_meta_group_scope;

CREATE TEMP TABLE _meta_group_year_usage ON COMMIT DROP AS
SELECT DISTINCT meta_group_id, academic_year
  FROM (
        SELECT cpe.meta_group_id, cpe.academic_year
          FROM curriculum_plan_entry cpe
         WHERE cpe.meta_group_id IS NOT NULL
           AND cpe.academic_year IS NOT NULL
        UNION ALL
        SELECT mle.meta_group_id, mle.academic_year
          FROM manual_load_entry mle
         WHERE mle.meta_group_id IS NOT NULL
           AND mle.academic_year IS NOT NULL
       ) used_years;

CREATE TEMP TABLE _meta_group_latest_year ON COMMIT DROP AS
SELECT meta_group_id,
       max(academic_year) AS latest_academic_year,
       count(*) AS year_count
  FROM _meta_group_year_usage
 GROUP BY meta_group_id;

UPDATE meta_group mg
   SET academic_year = latest.latest_academic_year
  FROM _meta_group_latest_year latest
 WHERE mg.id = latest.meta_group_id
   AND latest.year_count = 1;

UPDATE meta_group mg
   SET academic_year = latest.latest_academic_year
  FROM _meta_group_latest_year latest
 WHERE mg.id = latest.meta_group_id
   AND latest.year_count > 1;

CREATE TEMP TABLE _meta_group_year_copy (
    original_meta_group_id BIGINT NOT NULL,
    academic_year VARCHAR(9) NOT NULL,
    new_meta_group_id BIGINT
) ON COMMIT DROP;

INSERT INTO _meta_group_year_copy (original_meta_group_id, academic_year)
SELECT usage.meta_group_id,
       usage.academic_year
  FROM _meta_group_year_usage usage
  JOIN _meta_group_latest_year latest ON latest.meta_group_id = usage.meta_group_id
 WHERE latest.year_count > 1
   AND usage.academic_year <> latest.latest_academic_year;

WITH source_rows AS (
    SELECT copy.original_meta_group_id,
           copy.academic_year,
           mg.number_school_building,
           mg.building_group_id,
           mg.school_building_id,
           mg.parallel,
           mg.name,
           mg.class_type,
           mg.study_period_setting_id
      FROM _meta_group_year_copy copy
      JOIN meta_group mg ON mg.id = copy.original_meta_group_id
), inserted AS (
    INSERT INTO meta_group (
        number_school_building,
        building_group_id,
        school_building_id,
        parallel,
        name,
        class_type,
        study_period_setting_id,
        academic_year
    )
    SELECT number_school_building,
           building_group_id,
           school_building_id,
           parallel,
           name,
           class_type,
           study_period_setting_id,
           academic_year
      FROM source_rows
    RETURNING id,
              number_school_building,
              building_group_id,
              school_building_id,
              parallel,
              name,
              class_type,
              study_period_setting_id,
              academic_year
)
UPDATE _meta_group_year_copy copy
   SET new_meta_group_id = inserted.id
  FROM source_rows source
  JOIN inserted
    ON inserted.academic_year = source.academic_year
   AND inserted.number_school_building = source.number_school_building
   AND inserted.parallel = source.parallel
   AND inserted.name = source.name
   AND inserted.class_type = source.class_type
   AND inserted.building_group_id IS NOT DISTINCT FROM source.building_group_id
   AND inserted.school_building_id IS NOT DISTINCT FROM source.school_building_id
   AND inserted.study_period_setting_id IS NOT DISTINCT FROM source.study_period_setting_id
 WHERE copy.original_meta_group_id = source.original_meta_group_id
   AND copy.academic_year = source.academic_year;

UPDATE curriculum_plan_entry cpe
   SET meta_group_id = copy.new_meta_group_id
  FROM _meta_group_year_copy copy
 WHERE cpe.meta_group_id = copy.original_meta_group_id
   AND cpe.academic_year = copy.academic_year;

UPDATE manual_load_entry mle
   SET meta_group_id = copy.new_meta_group_id
  FROM _meta_group_year_copy copy
 WHERE mle.meta_group_id = copy.original_meta_group_id
   AND mle.academic_year = copy.academic_year;

DO $$
DECLARE
    orphan_copy_count BIGINT;
BEGIN
    SELECT count(*) INTO orphan_copy_count
      FROM _meta_group_year_copy
     WHERE new_meta_group_id IS NULL;

    IF orphan_copy_count > 0 THEN
        RAISE EXCEPTION 'Cannot assign meta_group.academic_year: failed to create historical meta group copies (% rows).', orphan_copy_count;
    END IF;
END $$;

ALTER TABLE meta_group
    ALTER COLUMN academic_year SET NOT NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
          FROM pg_constraint
         WHERE conname = 'uk_meta_group_year_scope'
           AND conrelid = 'meta_group'::regclass
           AND contype = 'u'
    ) THEN
        ALTER TABLE meta_group
            ADD CONSTRAINT uk_meta_group_year_scope
            UNIQUE (academic_year, number_school_building, parallel, name, class_type);
    END IF;
END $$;

DO $$
DECLARE
    missing_year_count BIGINT;
    curriculum_cross_year_count BIGINT;
    manual_cross_year_count BIGINT;
BEGIN
    SELECT count(*) INTO missing_year_count
      FROM meta_group
     WHERE academic_year IS NULL;

    IF missing_year_count > 0 THEN
        RAISE EXCEPTION 'meta_group rows without academic_year remain: %', missing_year_count;
    END IF;

    SELECT count(*) INTO curriculum_cross_year_count
      FROM curriculum_plan_entry cpe
      JOIN meta_group mg ON mg.id = cpe.meta_group_id
     WHERE cpe.meta_group_id IS NOT NULL
       AND cpe.academic_year <> mg.academic_year;

    IF curriculum_cross_year_count > 0 THEN
        RAISE EXCEPTION 'curriculum_plan_entry rows reference meta_group from another academic_year: %', curriculum_cross_year_count;
    END IF;

    SELECT count(*) INTO manual_cross_year_count
      FROM manual_load_entry mle
      JOIN meta_group mg ON mg.id = mle.meta_group_id
     WHERE mle.meta_group_id IS NOT NULL
       AND mle.academic_year <> mg.academic_year;

    IF manual_cross_year_count > 0 THEN
        RAISE EXCEPTION 'manual_load_entry rows reference meta_group from another academic_year: %', manual_cross_year_count;
    END IF;
END $$;

COMMIT;
