-- Run with psql after applying scripts/migrations/2026-06-01_meta_group_curriculum_fk_fix.sql.
-- The test data is created inside a transaction and rolled back; the final block
-- verifies that no tagged test rows remain after ROLLBACK.

BEGIN;

DO $$
DECLARE
    v_academic_year TEXT := '2099/2100__mg_fk_fix_test';
    v_building TEXT := 'СП_TEST_MG_FIX';
    v_class_id BIGINT;
    v_group_id BIGINT;
    v_regular_curriculum_id BIGINT;
    v_regular_class_id BIGINT;
    v_regular_meta_group_id BIGINT;
    v_meta_curriculum_id BIGINT;
    v_meta_class_id BIGINT;
    v_meta_group_id BIGINT;
    v_renamed_class_name TEXT;
BEGIN
    INSERT INTO classroom_leadership_entry(
        academic_year,
        number_school_building,
        class_name,
        class_direction,
        fio_teacher,
        campus_address,
        class_type,
        created_at
    ) VALUES (
        v_academic_year,
        v_building,
        '7-А',
        'Тест',
        'Тестовый классный руководитель',
        'Тестовый адрес',
        'NORMAL',
        now()
    ) RETURNING id INTO v_class_id;

    INSERT INTO meta_group(number_school_building, parallel, name, class_type, study_period_setting_id)
    VALUES (v_building, 7, '7 поток тест', 'NORMAL', NULL)
    RETURNING id INTO v_group_id;

    INSERT INTO curriculum_plan_entry(
        number_school_building,
        academic_year,
        stage,
        study_period,
        deprecated,
        class_name,
        subject_name,
        planned_hours,
        subgroup_required,
        subgroup_count,
        education_level,
        curriculum_part,
        meta_group,
        created_at
    ) VALUES (
        v_building,
        v_academic_year,
        'OOO',
        'YEAR',
        false,
        '7-А',
        'Тестовый предмет',
        1,
        false,
        0,
        'BASIC',
        'CORE',
        true,
        now()
    ) RETURNING id, class_id, meta_group_id
      INTO v_regular_curriculum_id, v_regular_class_id, v_regular_meta_group_id;

    IF v_regular_class_id IS DISTINCT FROM v_class_id THEN
        RAISE EXCEPTION 'Обычная строка meta_group=true должна сохранить class_id %, фактически %', v_class_id, v_regular_class_id;
    END IF;
    IF v_regular_meta_group_id IS NOT NULL THEN
        RAISE EXCEPTION 'Обычная строка meta_group=true должна иметь meta_group_id=NULL, фактически %', v_regular_meta_group_id;
    END IF;

    INSERT INTO curriculum_plan_entry(
        number_school_building,
        academic_year,
        stage,
        study_period,
        deprecated,
        class_name,
        subject_name,
        planned_hours,
        subgroup_required,
        subgroup_count,
        education_level,
        curriculum_part,
        meta_group,
        created_at
    ) VALUES (
        v_building,
        v_academic_year,
        'OOO',
        'YEAR',
        false,
        'МГ:7 поток тест',
        'Тестовый предмет',
        1,
        false,
        0,
        'BASIC',
        'CORE',
        true,
        now()
    ) RETURNING id, class_id, meta_group_id
      INTO v_meta_curriculum_id, v_meta_class_id, v_meta_group_id;

    IF v_meta_class_id IS NOT NULL THEN
        RAISE EXCEPTION 'Явная строка МГ должна иметь class_id=NULL, фактически %', v_meta_class_id;
    END IF;
    IF v_meta_group_id IS DISTINCT FROM v_group_id THEN
        RAISE EXCEPTION 'Явная строка МГ должна ссылаться на meta_group_id %, фактически %', v_group_id, v_meta_group_id;
    END IF;

    UPDATE meta_group
    SET name = '7 поток тест переименован'
    WHERE id = v_group_id;

    SELECT class_name
    INTO v_renamed_class_name
    FROM curriculum_plan_entry
    WHERE id = v_meta_curriculum_id;

    IF v_renamed_class_name <> 'МГ:7 поток тест переименован' THEN
        RAISE EXCEPTION 'Переименование метагруппы не протянулось в curriculum_plan_entry: %', v_renamed_class_name;
    END IF;
END $$;

ROLLBACK;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM curriculum_plan_entry WHERE academic_year = '2099/2100__mg_fk_fix_test') THEN
        RAISE EXCEPTION 'ROLLBACK не очистил тестовые строки curriculum_plan_entry';
    END IF;
    IF EXISTS (SELECT 1 FROM classroom_leadership_entry WHERE academic_year = '2099/2100__mg_fk_fix_test') THEN
        RAISE EXCEPTION 'ROLLBACK не очистил тестовые строки classroom_leadership_entry';
    END IF;
    IF EXISTS (SELECT 1 FROM meta_group WHERE number_school_building = 'СП_TEST_MG_FIX') THEN
        RAISE EXCEPTION 'ROLLBACK не очистил тестовые строки meta_group';
    END IF;
END $$;
