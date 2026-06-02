-- Read-only verification for the completed FK migration cutover state.
-- This script intentionally performs no DDL and no data changes.
-- Expected result for data-quality checks: issue_count = 0.

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
    SELECT 'curriculum number_school_building does not match building_group.code', count(*)::bigint
    FROM curriculum_plan_entry c
    JOIN building_group bg ON bg.id = c.building_group_id
    WHERE upper(replace(split_part(coalesce(c.number_school_building, ''), '|', 1), ' ', '')) <> bg.code
    UNION ALL
    SELECT 'manual load number_school_building does not match building_group.code', count(*)::bigint
    FROM manual_load_entry m
    JOIN building_group bg ON bg.id = m.building_group_id
    WHERE upper(replace(split_part(coalesce(m.number_school_building, ''), '|', 1), ' ', '')) <> bg.code
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

    -- FK target text should agree with class/meta-group references.
    UNION ALL
    SELECT 'curriculum class_id target does not match legacy class fields', count(*)::bigint
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
    SELECT 'manual class_id target does not match legacy class fields', count(*)::bigint
    FROM manual_load_entry m
    JOIN classroom_leadership_entry cl ON cl.id = m.class_id
    WHERE m.class_name NOT LIKE 'МГ:%'
      AND (
          coalesce(m.academic_year, '') <> coalesce(cl.academic_year, '')
          OR lower(trim(m.class_name)) <> lower(trim(cl.class_name))
          OR upper(replace(split_part(coalesce(m.number_school_building, ''), '|', 1), ' ', ''))
             <> upper(replace(split_part(coalesce(cl.number_school_building, ''), '|', 1), ' ', ''))
      )
    UNION ALL
    SELECT 'curriculum meta_group_id target does not match legacy meta fields', count(*)::bigint
    FROM curriculum_plan_entry c
    JOIN meta_group mg ON mg.id = c.meta_group_id
    WHERE c.class_name LIKE 'МГ:%'
      AND (
          lower(trim(regexp_replace(c.class_name, '^\s*МГ:', ''))) <> lower(trim(mg.name))
          OR upper(replace(split_part(coalesce(c.number_school_building, ''), '|', 1), ' ', ''))
             <> upper(replace(split_part(coalesce(mg.number_school_building, ''), '|', 1), ' ', ''))
      )
    UNION ALL
    SELECT 'manual meta_group_id target does not match legacy meta fields', count(*)::bigint
    FROM manual_load_entry m
    JOIN meta_group mg ON mg.id = m.meta_group_id
    WHERE m.class_name LIKE 'МГ:%'
      AND (
          lower(trim(regexp_replace(m.class_name, '^\s*МГ:', ''))) <> lower(trim(mg.name))
          OR upper(replace(split_part(coalesce(m.number_school_building, ''), '|', 1), ' ', ''))
             <> upper(replace(split_part(coalesce(mg.number_school_building, ''), '|', 1), ' ', ''))
      )

    -- Address/site diagnostics: concrete school_building_id is absent, so campus_address remains the only site discriminator.
    UNION ALL
    SELECT 'classroom campus_address has no matching school_building address in same building group', count(*)::bigint
    FROM classroom_leadership_entry c
    WHERE coalesce(trim(c.campus_address), '') <> ''
      AND NOT EXISTS (
          SELECT 1
          FROM school_building sb
          WHERE sb.building_group_id = c.building_group_id
            AND lower(trim(sb.address)) = lower(trim(c.campus_address))
      )
    UNION ALL
    SELECT 'classroom campus_address matches multiple school_building rows in same building group', count(*)::bigint
    FROM classroom_leadership_entry c
    WHERE coalesce(trim(c.campus_address), '') <> ''
      AND (
          SELECT count(*)
          FROM school_building sb
          WHERE sb.building_group_id = c.building_group_id
            AND lower(trim(sb.address)) = lower(trim(c.campus_address))
      ) > 1
)
SELECT check_name, issue_count
FROM checks
ORDER BY check_name;

-- Schema-presence diagnostic for the missing concrete site FK.
-- Expected current result from audited migrations/entities: school_building_id is absent on these tables.
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
