-- Read-only verification for the completed FK migration cutover state.
-- This script intentionally performs no DDL and no data changes.
-- Expected result for blocking FK data-quality checks: issue_count = 0.
-- Legacy snapshot mismatches are reported in a separate informational block below.

WITH checks AS (
    -- Required FK columns should be populated after the completed migrations/backfill.
    SELECT 'school_building.building_group_id is NULL' AS check_name, count(*)::bigint AS issue_count
    FROM school_building WHERE building_group_id IS NULL
    UNION ALL
    SELECT 'classroom_leadership_entry.building_group_id is NULL', count(*)::bigint
    FROM classroom_leadership_entry WHERE building_group_id IS NULL
    UNION ALL
    SELECT 'curriculum_plan_entry.building_group_id is NULL', count(*)::bigint
    FROM curriculum_plan_entry WHERE building_group_id IS NULL
    UNION ALL
    SELECT 'manual_load_entry.building_group_id is NULL', count(*)::bigint
    FROM manual_load_entry WHERE building_group_id IS NULL
    UNION ALL
    SELECT 'meta_group.building_group_id is NULL', count(*)::bigint
    FROM meta_group WHERE building_group_id IS NULL
    UNION ALL
    SELECT 'meta_group.academic_year is NULL', count(*)::bigint
    FROM meta_group
    WHERE academic_year IS NULL
    UNION ALL
    SELECT 'teacher_directory_entry.building_group_id is NULL', count(*)::bigint
    FROM teacher_directory_entry WHERE building_group_id IS NULL
    UNION ALL
    SELECT 'classroom_leadership_entry.teacher_id is NULL', count(*)::bigint
    FROM classroom_leadership_entry WHERE teacher_id IS NULL
    UNION ALL
    SELECT 'manual_load_entry.teacher_id is NULL', count(*)::bigint
    FROM manual_load_entry WHERE teacher_id IS NULL
    UNION ALL
    SELECT 'curriculum_plan_entry.subject_id is NULL', count(*)::bigint
    FROM curriculum_plan_entry WHERE subject_id IS NULL
    UNION ALL
    SELECT 'manual_load_entry.subject_id is NULL', count(*)::bigint
    FROM manual_load_entry WHERE subject_id IS NULL
    UNION ALL
    SELECT 'subject_catalog_entry.subject_area_id is NULL', count(*)::bigint
    FROM subject_catalog_entry WHERE subject_area_id IS NULL

    -- FK/string consistency snapshots.
    UNION ALL
    SELECT 'school_building code does not match building_group.code', count(*)::bigint
    FROM school_building sb
    JOIN building_group bg ON bg.id = sb.building_group_id
    WHERE upper(replace(split_part(coalesce(sb.code, ''), '|', 1), ' ', '')) <> bg.code
    UNION ALL
    SELECT 'classroom number_school_building does not match building_group.code', count(*)::bigint
    FROM classroom_leadership_entry c
    JOIN building_group bg ON bg.id = c.building_group_id
    WHERE upper(replace(split_part(coalesce(c.number_school_building, ''), '|', 1), ' ', '')) <> bg.code
    UNION ALL
    SELECT 'meta_group number_school_building does not match building_group.code', count(*)::bigint
    FROM meta_group mg
    JOIN building_group bg ON bg.id = mg.building_group_id
    WHERE upper(replace(split_part(coalesce(mg.number_school_building, ''), '|', 1), ' ', '')) <> bg.code
    UNION ALL
    SELECT 'teacher number_school_building does not match building_group.code', count(*)::bigint
    FROM teacher_directory_entry t
    JOIN building_group bg ON bg.id = t.building_group_id
    WHERE upper(replace(split_part(coalesce(t.number_school_building, ''), '|', 1), ' ', '')) <> bg.code
    UNION ALL
    SELECT 'classroom fio_teacher does not match teacher_id', count(*)::bigint
    FROM classroom_leadership_entry c
    JOIN teacher_directory_entry t ON t.id = c.teacher_id
    WHERE lower(trim(c.fio_teacher)) <> lower(trim(t.fio_teacher))
    UNION ALL
    SELECT 'manual load fio_teacher does not match teacher_id', count(*)::bigint
    FROM manual_load_entry m
    JOIN teacher_directory_entry t ON t.id = m.teacher_id
    WHERE lower(trim(m.fio_teacher)) <> lower(trim(t.fio_teacher))
    UNION ALL
    SELECT 'curriculum subject_name does not match subject_id', count(*)::bigint
    FROM curriculum_plan_entry c
    JOIN subject_catalog_entry s ON s.id = c.subject_id
    WHERE lower(trim(c.subject_name)) <> lower(trim(s.subject_name))
    UNION ALL
    SELECT 'manual load subject_name does not match subject_id', count(*)::bigint
    FROM manual_load_entry m
    JOIN subject_catalog_entry s ON s.id = m.subject_id
    WHERE lower(trim(m.subject_name)) <> lower(trim(s.subject_name))
    UNION ALL
    SELECT 'subject_area_name does not match subject_area_id', count(*)::bigint
    FROM subject_catalog_entry s
    JOIN subject_area a ON a.id = s.subject_area_id
    WHERE lower(trim(s.subject_area_name)) <> lower(trim(a.name))

    -- Class and meta-group semantics.
    UNION ALL
    SELECT 'regular curriculum rows without class_id', count(*)::bigint
    FROM curriculum_plan_entry
    WHERE class_name NOT LIKE 'МГ:%' AND class_id IS NULL
    UNION ALL
    SELECT 'regular manual-load rows without class_id', count(*)::bigint
    FROM manual_load_entry
    WHERE class_name NOT LIKE 'МГ:%' AND class_id IS NULL
    UNION ALL
    SELECT 'meta curriculum rows without meta_group_id', count(*)::bigint
    FROM curriculum_plan_entry
    WHERE class_name LIKE 'МГ:%' AND meta_group_id IS NULL
    UNION ALL
    SELECT 'meta manual-load rows without meta_group_id', count(*)::bigint
    FROM manual_load_entry
    WHERE class_name LIKE 'МГ:%' AND meta_group_id IS NULL
    UNION ALL
    SELECT 'curriculum rows with both class_id and meta_group_id set', count(*)::bigint
    FROM curriculum_plan_entry
    WHERE class_id IS NOT NULL AND meta_group_id IS NOT NULL
    UNION ALL
    SELECT 'manual-load rows with both class_id and meta_group_id set', count(*)::bigint
    FROM manual_load_entry
    WHERE class_id IS NOT NULL AND meta_group_id IS NOT NULL
    UNION ALL
    SELECT 'curriculum explicit meta rows with class_id set', count(*)::bigint
    FROM curriculum_plan_entry
    WHERE class_name LIKE 'МГ:%' AND class_id IS NOT NULL
    UNION ALL
    SELECT 'manual explicit meta rows with class_id set', count(*)::bigint
    FROM manual_load_entry
    WHERE class_name LIKE 'МГ:%' AND class_id IS NOT NULL
    UNION ALL
    SELECT 'curriculum regular rows with meta_group_id set', count(*)::bigint
    FROM curriculum_plan_entry
    WHERE class_name NOT LIKE 'МГ:%' AND meta_group_id IS NOT NULL
    UNION ALL
    SELECT 'manual regular rows with meta_group_id set', count(*)::bigint
    FROM manual_load_entry
    WHERE class_name NOT LIKE 'МГ:%' AND meta_group_id IS NOT NULL
    UNION ALL
    SELECT 'curriculum class_id target is missing', count(*)::bigint
    FROM curriculum_plan_entry c
    LEFT JOIN classroom_leadership_entry cl ON cl.id = c.class_id
    WHERE c.class_id IS NOT NULL AND cl.id IS NULL
    UNION ALL
    SELECT 'manual class_id target is missing', count(*)::bigint
    FROM manual_load_entry m
    LEFT JOIN classroom_leadership_entry cl ON cl.id = m.class_id
    WHERE m.class_id IS NOT NULL AND cl.id IS NULL
    UNION ALL
    SELECT 'curriculum meta_group_id target is missing', count(*)::bigint
    FROM curriculum_plan_entry c
    LEFT JOIN meta_group mg ON mg.id = c.meta_group_id
    WHERE c.meta_group_id IS NOT NULL AND mg.id IS NULL
    UNION ALL
    SELECT 'manual meta_group_id target is missing', count(*)::bigint
    FROM manual_load_entry m
    LEFT JOIN meta_group mg ON mg.id = m.meta_group_id
    WHERE m.meta_group_id IS NOT NULL AND mg.id IS NULL

    UNION ALL
    SELECT 'curriculum academic_year does not match meta_group.academic_year', count(*)::bigint
    FROM curriculum_plan_entry cpe
    JOIN meta_group mg ON mg.id = cpe.meta_group_id
    WHERE cpe.meta_group_id IS NOT NULL
      AND cpe.academic_year <> mg.academic_year
    UNION ALL
    SELECT 'manual load academic_year does not match meta_group.academic_year', count(*)::bigint
    FROM manual_load_entry mle
    JOIN meta_group mg ON mg.id = mle.meta_group_id
    WHERE mle.meta_group_id IS NOT NULL
      AND mle.academic_year <> mg.academic_year


    -- Address/site diagnostics: physical site matching is independent from the class building_group_id.
    UNION ALL
    SELECT 'classroom campus_address has no matching school_building address', count(*)::bigint
    FROM classroom_leadership_entry c
    WHERE coalesce(trim(c.campus_address), '') <> ''
      AND NOT EXISTS (
          SELECT 1
          FROM school_building sb
          WHERE lower(regexp_replace(trim(coalesce(sb.address, '')), '\s+', ' ', 'g')) =
                lower(regexp_replace(trim(coalesce(c.campus_address, '')), '\s+', ' ', 'g'))
      )
    UNION ALL
    SELECT 'classroom campus_address matches multiple school_building rows', count(*)::bigint
    FROM classroom_leadership_entry c
    WHERE coalesce(trim(c.campus_address), '') <> ''
      AND (
          SELECT count(*)
          FROM school_building sb
          WHERE lower(regexp_replace(trim(coalesce(sb.address, '')), '\s+', ' ', 'g')) =
                lower(regexp_replace(trim(coalesce(c.campus_address, '')), '\s+', ' ', 'g'))
      ) > 1
)
SELECT check_name, issue_count
FROM checks
ORDER BY check_name;

-- Informational legacy snapshot mismatch report.
-- These rows should not block FK-only operations after PR 3 because relations are resolved by class_id/meta_group_id.
WITH informational_legacy_snapshot_mismatch AS (
    SELECT 'informational legacy snapshot mismatch: curriculum number_school_building does not match building_group.code' AS check_name,
           count(*)::bigint AS row_count
    FROM curriculum_plan_entry c
    JOIN building_group bg ON bg.id = c.building_group_id
    WHERE upper(replace(split_part(coalesce(c.number_school_building, ''), '|', 1), ' ', '')) <> bg.code
    UNION ALL
    SELECT 'informational legacy snapshot mismatch: manual load number_school_building does not match building_group.code',
           count(*)::bigint
    FROM manual_load_entry m
    JOIN building_group bg ON bg.id = m.building_group_id
    WHERE upper(replace(split_part(coalesce(m.number_school_building, ''), '|', 1), ' ', '')) <> bg.code
    UNION ALL
    SELECT 'informational legacy snapshot mismatch: curriculum class_id target text differs from legacy class fields',
           count(*)::bigint
    FROM curriculum_plan_entry c
    JOIN classroom_leadership_entry cl ON cl.id = c.class_id
    WHERE c.class_name NOT LIKE 'МГ:%'
      AND (
          coalesce(c.academic_year, '') <> coalesce(cl.academic_year, '')
          OR lower(trim(c.class_name)) <> lower(trim(cl.class_name))
          OR upper(replace(split_part(coalesce(c.number_school_building, ''), '|', 1), ' ', ''))
             <> upper(replace(split_part(coalesce(cl.number_school_building, ''), '|', 1), ' ', ''))
      )
    UNION ALL
    SELECT 'informational legacy snapshot mismatch: manual class_id target text differs from legacy class fields',
           count(*)::bigint
    FROM manual_load_entry m
    JOIN classroom_leadership_entry cl ON cl.id = m.class_id
    WHERE m.class_name NOT LIKE 'МГ:%'
      AND (
          coalesce(m.academic_year, '') <> coalesce(cl.academic_year, '')
          OR lower(trim(m.class_name)) <> lower(trim(cl.class_name))
          OR upper(replace(split_part(coalesce(m.number_school_building, ''), '|', 1), ' ', ''))
             <> upper(replace(split_part(coalesce(cl.number_school_building, ''), '|', 1), ' ', ''))
      )
)
SELECT check_name, row_count
FROM informational_legacy_snapshot_mismatch
ORDER BY check_name;

-- Schema-presence diagnostic for the concrete site FK.
-- Before PR 2 this is absent; after the classroom FK migration it should exist on classroom_leadership_entry.
SELECT table_name,
       EXISTS (
           SELECT 1
           FROM information_schema.columns c
           WHERE c.table_schema = current_schema()
             AND c.table_name = v.table_name
             AND c.column_name = 'school_building_id'
       ) AS has_school_building_id
FROM (VALUES
    ('classroom_leadership_entry'),
    ('curriculum_plan_entry'),
    ('manual_load_entry'),
    ('meta_group')
) AS v(table_name)
ORDER BY table_name;

-- Teacher/subject/subject-area FK cutover diagnostics for the current PR.
-- These are blocking FK checks for working relations; legacy text mismatches remain informational snapshots below.
WITH teacher_subject_area_fk_checks AS (
    SELECT 'curriculum_plan_entry.subject_id is NULL' AS check_name, count(*)::bigint AS issue_count
    FROM curriculum_plan_entry
    WHERE subject_id IS NULL
    UNION ALL
    SELECT 'manual_load_entry.subject_id is NULL', count(*)::bigint
    FROM manual_load_entry
    WHERE subject_id IS NULL
    UNION ALL
    SELECT 'manual_load_entry.teacher_id is NULL', count(*)::bigint
    FROM manual_load_entry
    WHERE teacher_id IS NULL
    UNION ALL
    SELECT 'subject_catalog_entry.subject_area_id is NULL', count(*)::bigint
    FROM subject_catalog_entry
    WHERE subject_area_id IS NULL
    UNION ALL
    SELECT 'curriculum_plan_entry.subject_id target missing', count(*)::bigint
    FROM curriculum_plan_entry c
    LEFT JOIN subject_catalog_entry s ON s.id = c.subject_id
    WHERE c.subject_id IS NOT NULL AND s.id IS NULL
    UNION ALL
    SELECT 'manual_load_entry.subject_id target missing', count(*)::bigint
    FROM manual_load_entry m
    LEFT JOIN subject_catalog_entry s ON s.id = m.subject_id
    WHERE m.subject_id IS NOT NULL AND s.id IS NULL
    UNION ALL
    SELECT 'manual_load_entry.teacher_id target missing', count(*)::bigint
    FROM manual_load_entry m
    LEFT JOIN teacher_directory_entry t ON t.id = m.teacher_id
    WHERE m.teacher_id IS NOT NULL AND t.id IS NULL
    UNION ALL
    SELECT 'subject_catalog_entry.subject_area_id target missing', count(*)::bigint
    FROM subject_catalog_entry s
    LEFT JOIN subject_area a ON a.id = s.subject_area_id
    WHERE s.subject_area_id IS NOT NULL AND a.id IS NULL
    UNION ALL
    SELECT 'school_building.building_group_id is NULL', count(*)::bigint
    FROM school_building
    WHERE building_group_id IS NULL
)
SELECT check_name, issue_count
FROM teacher_subject_area_fk_checks
ORDER BY check_name;

-- Informational unresolved teacher model question: do not auto-backfill this in FK cutover PRs.
SELECT 'informational unresolved design: teacher_directory_entry.building_group_id is NULL' AS check_name,
       count(*)::bigint AS row_count
FROM teacher_directory_entry
WHERE building_group_id IS NULL;
