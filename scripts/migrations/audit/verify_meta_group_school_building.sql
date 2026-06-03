-- Read-only diagnostics for the meta_group -> school_building cutover.
-- This script intentionally performs no DDL, DML, or automatic duplicate cleanup.

SELECT 'meta groups without school_building_id' AS check_name,
       mg.id,
       mg.number_school_building,
       mg.parallel,
       mg.name,
       mg.class_type
  FROM meta_group mg
 WHERE mg.school_building_id IS NULL
 ORDER BY mg.number_school_building, mg.parallel, mg.name;

SELECT 'manual-load metagroup rows whose parent has no school_building_id' AS check_name,
       m.id,
       m.academic_year,
       m.number_school_building,
       m.class_name,
       m.meta_group_id,
       m.subject_id,
       m.subject_name,
       m.study_period,
       m.load_from_date,
       m.load_to_date,
       m.fio_teacher
  FROM manual_load_entry m
  JOIN meta_group mg ON mg.id = m.meta_group_id
 WHERE m.meta_group_id IS NOT NULL
   AND mg.school_building_id IS NULL
 ORDER BY m.academic_year, m.meta_group_id, m.subject_name, m.study_period, m.load_from_date, m.load_to_date, m.id;

WITH metagroup_load_duplicates AS (
    SELECT m.academic_year,
           m.meta_group_id,
           COALESCE(m.subject_id::text, lower(trim(m.subject_name))) AS subject_key,
           m.subject_id,
           m.subject_name,
           m.study_period,
           m.load_from_date,
           m.load_to_date,
           COUNT(*) AS duplicate_count,
           array_agg(m.id ORDER BY m.id) AS manual_load_ids,
           array_agg(m.fio_teacher ORDER BY m.id) AS teachers
      FROM manual_load_entry m
     WHERE m.meta_group_id IS NOT NULL
     GROUP BY m.academic_year,
              m.meta_group_id,
              COALESCE(m.subject_id::text, lower(trim(m.subject_name))),
              m.subject_id,
              m.subject_name,
              m.study_period,
              m.load_from_date,
              m.load_to_date
    HAVING COUNT(*) > 1
)
SELECT 'duplicate metagroup manual-load rows' AS check_name,
       d.*
  FROM metagroup_load_duplicates d
 ORDER BY d.academic_year, d.meta_group_id, d.subject_name, d.study_period, d.load_from_date, d.load_to_date;
