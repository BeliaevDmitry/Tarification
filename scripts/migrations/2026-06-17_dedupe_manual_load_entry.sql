-- Удаляет полные дубли сохранённой нагрузки.
-- Оставляет одну строку с минимальным id, удаляет только строки, совпадающие по смысловым полям нагрузки.
--
-- Перед запуском можно посмотреть, что будет удалено:
-- WITH ranked AS (
--     SELECT id,
--            row_number() OVER (
--                PARTITION BY
--                    academic_year,
--                    coalesce(teacher_id, -1),
--                    lower(trim(coalesce(fio_teacher, ''))),
--                    coalesce(building_group_id, -1),
--                    lower(trim(coalesce(number_school_building, ''))),
--                    coalesce(school_building_id, -1),
--                    coalesce(subject_id, -1),
--                    lower(trim(coalesce(subject_name, ''))),
--                    coalesce(class_id, -1),
--                    coalesce(meta_group_id, -1),
--                    lower(trim(coalesce(class_name, ''))),
--                    lower(trim(coalesce(group_name_educational_plan, ''))),
--                    coalesce(group_load, load, 0),
--                    coalesce(load, 0),
--                    coalesce(education_level, ''),
--                    coalesce(study_period, 'YEAR'),
--                    load_from_date,
--                    load_to_date
--                ORDER BY id
--            ) AS rn
--     FROM manual_load_entry
-- )
-- SELECT *
-- FROM manual_load_entry
-- WHERE id IN (SELECT id FROM ranked WHERE rn > 1)
-- ORDER BY academic_year, fio_teacher, class_name, subject_name, load_from_date, id;

BEGIN;

WITH ranked AS (
    SELECT id,
           row_number() OVER (
               PARTITION BY
                   academic_year,
                   coalesce(teacher_id, -1),
                   lower(trim(coalesce(fio_teacher, ''))),
                   coalesce(building_group_id, -1),
                   lower(trim(coalesce(number_school_building, ''))),
                   coalesce(school_building_id, -1),
                   coalesce(subject_id, -1),
                   lower(trim(coalesce(subject_name, ''))),
                   coalesce(class_id, -1),
                   coalesce(meta_group_id, -1),
                   lower(trim(coalesce(class_name, ''))),
                   lower(trim(coalesce(group_name_educational_plan, ''))),
                   coalesce(group_load, load, 0),
                   coalesce(load, 0),
                   coalesce(education_level, ''),
                   coalesce(study_period, 'YEAR'),
                   load_from_date,
                   load_to_date
               ORDER BY id
           ) AS rn
    FROM manual_load_entry
),
deleted AS (
    DELETE FROM manual_load_entry
    WHERE id IN (SELECT id FROM ranked WHERE rn > 1)
    RETURNING id
)
SELECT count(*) AS deleted_duplicate_manual_load_rows
FROM deleted;

COMMIT;
