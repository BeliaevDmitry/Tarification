-- Run with psql after applying scripts/migrations/2026-06-05_meta_group_year_aware_sync_trigger.sql.
-- The test data is created inside a transaction and rolled back.

BEGIN;

DO $$
DECLARE
    v_building TEXT := 'СП_TEST_MG_YEAR_SYNC';
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
    INSERT INTO meta_group(academic_year, number_school_building, parallel, name, class_type, study_period_setting_id)
    VALUES ('2025/2026', v_building, 4, '4 4ЦЧ-СВЕТСКАЯ', 'NORMAL', NULL)
    RETURNING id INTO v_mg_2025;

    INSERT INTO meta_group(academic_year, number_school_building, parallel, name, class_type, study_period_setting_id)
    VALUES ('2026/2027', v_building, 4, '4 4ЦЧ-СВЕТСКАЯ', 'NORMAL', NULL)
    RETURNING id INTO v_mg_2026;

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
        '2025/2026',
        'OOO',
        'YEAR',
        false,
        'МГ:4 4ЦЧ-СВЕТСКАЯ',
        'Тестовый предмет 2025',
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
        '2026/2027',
        'OOO',
        'YEAR',
        false,
        'МГ:4 4ЦЧ-СВЕТСКАЯ',
        'Тестовый предмет 2026',
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
        number_school_building,
        subject_name,
        class_name,
        load,
        study_period,
        education_level,
        created_at
    ) VALUES (
        '2025/2026',
        'Тестовый педагог',
        v_building,
        'Тестовый предмет 2025',
        'МГ:4 4ЦЧ-СВЕТСКАЯ',
        1,
        'YEAR',
        'BASIC',
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
        number_school_building,
        subject_name,
        class_name,
        load,
        study_period,
        education_level,
        created_at
    ) VALUES (
        '2026/2027',
        'Тестовый педагог',
        v_building,
        'Тестовый предмет 2026',
        'МГ:4 4ЦЧ-СВЕТСКАЯ',
        1,
        'YEAR',
        'BASIC',
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
            meta_group_id,
            created_at
        ) VALUES (
            v_building,
            '2025/2026',
            'OOO',
            'YEAR',
            false,
            'МГ:4 4ЦЧ-СВЕТСКАЯ',
            'Тестовый cross-year предмет',
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
END $$;

ROLLBACK;
