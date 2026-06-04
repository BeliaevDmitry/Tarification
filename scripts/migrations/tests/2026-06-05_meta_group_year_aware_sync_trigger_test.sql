-- Run with psql after applying scripts/migrations/2026-06-05_meta_group_year_aware_sync_trigger.sql.
-- The test data is created inside a transaction and rolled back.

BEGIN;

DO $$
DECLARE
    v_building TEXT := 'СП_TEST_MG_YEAR_SYNC';
    v_meta_name TEXT := '4 4ЦЧ-СВЕТСКАЯ';
    v_missing_meta_name TEXT := '4 4ЦЧ-НЕСУЩЕСТВУЮЩАЯ';
    v_building_group_id BIGINT;
    v_school_building_id BIGINT;
    v_subject_2025_id BIGINT;
    v_subject_2026_id BIGINT;
    v_teacher_id BIGINT;
    v_mg_2025 BIGINT;
    v_mg_2026 BIGINT;
    v_curriculum_2025_mg BIGINT;
    v_curriculum_2025_class BIGINT;
    v_curriculum_2026_mg BIGINT;
    v_curriculum_2026_class BIGINT;
    v_manual_2025_mg BIGINT;
    v_manual_2025_class BIGINT;
    v_manual_2026_mg BIGINT;
    v_manual_2026_class BIGINT;
BEGIN
    INSERT INTO building_group(code, name)
    VALUES (v_building, 'Тестовая группа корпусов для МГ')
    ON CONFLICT (code) DO UPDATE SET name = EXCLUDED.name
    RETURNING id INTO v_building_group_id;

    INSERT INTO school_building(code, building_group_id, name, manager_fio, address, created_at)
    VALUES (v_building, v_building_group_id, 'Тестовая физическая площадка МГ', 'Тестовый руководитель', 'Тестовый адрес МГ', now())
    RETURNING id INTO v_school_building_id;

    INSERT INTO subject_catalog_entry(subject_name, subject_type, subject_area_name, subject_coefficient, created_at)
    VALUES ('Тестовый предмет МГ 2025', 'CORE', 'Без области', 1, now())
    ON CONFLICT (subject_name, subject_type) DO UPDATE SET subject_area_name = EXCLUDED.subject_area_name
    RETURNING id INTO v_subject_2025_id;

    INSERT INTO subject_catalog_entry(subject_name, subject_type, subject_area_name, subject_coefficient, created_at)
    VALUES ('Тестовый предмет МГ 2026', 'CORE', 'Без области', 1, now())
    ON CONFLICT (subject_name, subject_type) DO UPDATE SET subject_area_name = EXCLUDED.subject_area_name
    RETURNING id INTO v_subject_2026_id;

    INSERT INTO teacher_directory_entry(fio_teacher, number_school_building, building_group_id, created_at)
    VALUES ('Тестовый педагог МГ trigger', v_building, v_building_group_id, now())
    ON CONFLICT (fio_teacher) DO UPDATE
    SET number_school_building = EXCLUDED.number_school_building,
        building_group_id = EXCLUDED.building_group_id
    RETURNING id INTO v_teacher_id;

    INSERT INTO meta_group(
        academic_year,
        number_school_building,
        building_group_id,
        school_building_id,
        parallel,
        name,
        class_type,
        study_period_setting_id
    ) VALUES (
        '2025/2026',
        v_building,
        v_building_group_id,
        v_school_building_id,
        4,
        v_meta_name,
        'NORMAL',
        NULL
    ) RETURNING id INTO v_mg_2025;

    INSERT INTO meta_group(
        academic_year,
        number_school_building,
        building_group_id,
        school_building_id,
        parallel,
        name,
        class_type,
        study_period_setting_id
    ) VALUES (
        '2026/2027',
        v_building,
        v_building_group_id,
        v_school_building_id,
        4,
        v_meta_name,
        'NORMAL',
        NULL
    ) RETURNING id INTO v_mg_2026;

    INSERT INTO curriculum_plan_entry(
        number_school_building,
        building_group_id,
        academic_year,
        stage,
        study_period,
        deprecated,
        class_name,
        subject_name,
        subject_id,
        planned_hours,
        subgroup_required,
        subgroup_count,
        education_level,
        curriculum_part,
        meta_group,
        created_at
    ) VALUES (
        v_building,
        v_building_group_id,
        '2025/2026',
        'OOO',
        'YEAR',
        false,
        'МГ:' || v_meta_name,
        'Тестовый предмет МГ 2025',
        v_subject_2025_id,
        1,
        false,
        0,
        'BASIC',
        'CORE',
        true,
        now()
    ) RETURNING meta_group_id, class_id INTO v_curriculum_2025_mg, v_curriculum_2025_class;

    IF v_curriculum_2025_mg IS DISTINCT FROM v_mg_2025 THEN
        RAISE EXCEPTION '2025/2026 curriculum explicit МГ row resolved to %, expected %', v_curriculum_2025_mg, v_mg_2025;
    END IF;
    IF v_curriculum_2025_class IS NOT NULL THEN
        RAISE EXCEPTION '2025/2026 curriculum explicit МГ row should have class_id=NULL, got %', v_curriculum_2025_class;
    END IF;

    INSERT INTO curriculum_plan_entry(
        number_school_building,
        building_group_id,
        academic_year,
        stage,
        study_period,
        deprecated,
        class_name,
        subject_name,
        subject_id,
        planned_hours,
        subgroup_required,
        subgroup_count,
        education_level,
        curriculum_part,
        meta_group,
        created_at
    ) VALUES (
        v_building,
        v_building_group_id,
        '2026/2027',
        'OOO',
        'YEAR',
        false,
        'МГ:' || v_meta_name,
        'Тестовый предмет МГ 2026',
        v_subject_2026_id,
        1,
        false,
        0,
        'BASIC',
        'CORE',
        true,
        now()
    ) RETURNING meta_group_id, class_id INTO v_curriculum_2026_mg, v_curriculum_2026_class;

    IF v_curriculum_2026_mg IS DISTINCT FROM v_mg_2026 THEN
        RAISE EXCEPTION '2026/2027 curriculum explicit МГ row resolved to %, expected %', v_curriculum_2026_mg, v_mg_2026;
    END IF;
    IF v_curriculum_2026_class IS NOT NULL THEN
        RAISE EXCEPTION '2026/2027 curriculum explicit МГ row should have class_id=NULL, got %', v_curriculum_2026_class;
    END IF;

    INSERT INTO manual_load_entry(
        academic_year,
        fio_teacher,
        teacher_id,
        number_school_building,
        building_group_id,
        subject_name,
        subject_id,
        class_name,
        load,
        study_period,
        education_level,
        dismissal_adjusted,
        orphaned,
        continuity_status,
        created_at
    ) VALUES (
        '2025/2026',
        'Тестовый педагог МГ trigger',
        v_teacher_id,
        v_building,
        v_building_group_id,
        'Тестовый предмет МГ 2025',
        v_subject_2025_id,
        'МГ:' || v_meta_name,
        1,
        'YEAR',
        'BASIC',
        false,
        false,
        'UNKNOWN',
        now()
    ) RETURNING meta_group_id, class_id INTO v_manual_2025_mg, v_manual_2025_class;

    IF v_manual_2025_mg IS DISTINCT FROM v_mg_2025 THEN
        RAISE EXCEPTION '2025/2026 manual-load explicit МГ row resolved to %, expected %', v_manual_2025_mg, v_mg_2025;
    END IF;
    IF v_manual_2025_class IS NOT NULL THEN
        RAISE EXCEPTION '2025/2026 manual-load explicit МГ row should have class_id=NULL, got %', v_manual_2025_class;
    END IF;

    INSERT INTO manual_load_entry(
        academic_year,
        fio_teacher,
        teacher_id,
        number_school_building,
        building_group_id,
        subject_name,
        subject_id,
        class_name,
        load,
        study_period,
        education_level,
        dismissal_adjusted,
        orphaned,
        continuity_status,
        created_at
    ) VALUES (
        '2026/2027',
        'Тестовый педагог МГ trigger',
        v_teacher_id,
        v_building,
        v_building_group_id,
        'Тестовый предмет МГ 2026',
        v_subject_2026_id,
        'МГ:' || v_meta_name,
        1,
        'YEAR',
        'BASIC',
        false,
        false,
        'UNKNOWN',
        now()
    ) RETURNING meta_group_id, class_id INTO v_manual_2026_mg, v_manual_2026_class;

    IF v_manual_2026_mg IS DISTINCT FROM v_mg_2026 THEN
        RAISE EXCEPTION '2026/2027 manual-load explicit МГ row resolved to %, expected %', v_manual_2026_mg, v_mg_2026;
    END IF;
    IF v_manual_2026_class IS NOT NULL THEN
        RAISE EXCEPTION '2026/2027 manual-load explicit МГ row should have class_id=NULL, got %', v_manual_2026_class;
    END IF;

    BEGIN
        INSERT INTO curriculum_plan_entry(
            number_school_building,
            building_group_id,
            academic_year,
            stage,
            study_period,
            deprecated,
            class_name,
            subject_name,
            subject_id,
            planned_hours,
            subgroup_required,
            subgroup_count,
            education_level,
            curriculum_part,
            meta_group,
            meta_group_id,
            created_at
        ) VALUES (
            v_building,
            v_building_group_id,
            '2025/2026',
            'OOO',
            'YEAR',
            false,
            'МГ:' || v_meta_name,
            'Тестовый предмет МГ 2025',
            v_subject_2025_id,
            1,
            false,
            0,
            'BASIC',
            'CORE',
            true,
            v_mg_2026,
            now()
        );
        RAISE EXCEPTION 'Cross-year explicit meta_group_id was accepted unexpectedly';
    EXCEPTION WHEN raise_exception THEN
        IF SQLERRM = 'Cross-year explicit meta_group_id was accepted unexpectedly' THEN
            RAISE;
        END IF;
    END;

    BEGIN
        INSERT INTO curriculum_plan_entry(
            number_school_building,
            building_group_id,
            academic_year,
            stage,
            study_period,
            deprecated,
            class_name,
            subject_name,
            subject_id,
            planned_hours,
            subgroup_required,
            subgroup_count,
            education_level,
            curriculum_part,
            meta_group,
            created_at
        ) VALUES (
            v_building,
            v_building_group_id,
            '2025/2026',
            'OOO',
            'YEAR',
            false,
            'МГ:' || v_missing_meta_name,
            'Тестовый предмет МГ 2025',
            v_subject_2025_id,
            1,
            false,
            0,
            'BASIC',
            'CORE',
            true,
            now()
        );
        RAISE EXCEPTION 'Missing meta group explicit row was accepted unexpectedly';
    EXCEPTION WHEN raise_exception THEN
        IF SQLERRM = 'Missing meta group explicit row was accepted unexpectedly' THEN
            RAISE;
        END IF;
        IF SQLERRM NOT LIKE 'Meta group not found for academic_year=%, building=%, name=%. Create the meta group with a physical school building before saving curriculum/manual-load rows.' THEN
            RAISE EXCEPTION 'Unexpected missing meta group error: %', SQLERRM;
        END IF;
    END;
END $$;

ROLLBACK;
