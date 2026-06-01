-- Fix curriculum class_id/meta_group_id semantics for metagroup participants.
-- The metagroup row itself is identified only by class_name LIKE 'МГ:%'.
-- Ordinary class rows may have meta_group=true as members, but they keep class_id
-- and must not point to meta_group_id.

BEGIN;

ALTER TABLE curriculum_plan_entry
    DROP CONSTRAINT IF EXISTS chk_curriculum_class_id_for_regular_class;

ALTER TABLE curriculum_plan_entry
    DROP CONSTRAINT IF EXISTS chk_curriculum_meta_group_id_for_meta;

CREATE OR REPLACE FUNCTION trg_sync_class_fk() RETURNS trigger AS $$
DECLARE
    cid BIGINT;
    cname TEXT;
    bcode TEXT;
    ctype TEXT;
BEGIN
    IF NEW.class_name LIKE 'МГ:%' THEN
        NEW.class_id := NULL;
        RETURN NEW;
    END IF;

    IF NEW.class_id IS NULL THEN
        IF coalesce(trim(NEW.class_name), '') = '' THEN
            RAISE EXCEPTION 'class_name is required for %', TG_TABLE_NAME;
        END IF;
        IF coalesce(trim(NEW.number_school_building), '') = '' THEN
            RAISE EXCEPTION 'number_school_building is required for class % in %', NEW.class_name, TG_TABLE_NAME;
        END IF;

        SELECT id, class_name, number_school_building, class_type
        INTO cid, cname, bcode, ctype
        FROM classroom_leadership_entry
        WHERE coalesce(academic_year, '') = coalesce(NEW.academic_year, '')
          AND upper(replace(split_part(coalesce(number_school_building, ''), '|', 1), ' ', '')) = upper(replace(split_part(coalesce(NEW.number_school_building, ''), '|', 1), ' ', ''))
          AND lower(trim(class_name)) = lower(trim(NEW.class_name))
        ORDER BY id
        LIMIT 1;

        IF cid IS NULL THEN
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
                coalesce(NEW.academic_year, ''),
                NEW.number_school_building,
                trim(NEW.class_name),
                'Не указана',
                'Вакансия',
                '',
                'NORMAL',
                now()
            )
            ON CONFLICT ON CONSTRAINT uk_classroom_leadership_class_building DO UPDATE
                SET number_school_building = EXCLUDED.number_school_building
            RETURNING id, class_name, number_school_building, class_type
            INTO cid, cname, bcode, ctype;
        END IF;

        NEW.class_id := cid;
        NEW.class_name := cname;
        NEW.number_school_building := bcode;
    ELSE
        SELECT class_name, number_school_building, class_type
        INTO cname, bcode, ctype
        FROM classroom_leadership_entry
        WHERE id = NEW.class_id;

        IF cname IS NULL THEN
            RAISE EXCEPTION 'class_id=% not found for %', NEW.class_id, TG_TABLE_NAME;
        END IF;

        NEW.class_name := cname;
        NEW.number_school_building := bcode;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION trg_sync_meta_group_fk() RETURNS trigger AS $$
DECLARE
    mid BIGINT;
    mname TEXT;
    bcode TEXT;
    parallel_value INTEGER;
BEGIN
    IF NEW.class_name NOT LIKE 'МГ:%' THEN
        NEW.meta_group_id := NULL;
        RETURN NEW;
    END IF;

    IF NEW.meta_group_id IS NULL THEN
        IF coalesce(trim(regexp_replace(NEW.class_name, '^\s*МГ:', '')), '') = '' THEN
            RAISE EXCEPTION 'meta group class_name is required for %', TG_TABLE_NAME;
        END IF;

        SELECT id, name, number_school_building, parallel
        INTO mid, mname, bcode, parallel_value
        FROM meta_group
        WHERE lower(trim(number_school_building)) = lower(trim(NEW.number_school_building))
          AND lower(trim(name)) = lower(trim(regexp_replace(NEW.class_name, '^\s*МГ:', '')))
        ORDER BY id
        LIMIT 1;

        IF mid IS NULL THEN
            parallel_value := greatest(1, least(11, coalesce(nullif(substring(regexp_replace(regexp_replace(NEW.class_name, '^\s*МГ:', ''), '^\s+', '') from '^(\d{1,2})'), '')::int, 1)));
            INSERT INTO meta_group(number_school_building, parallel, name, class_type, study_period_setting_id)
            VALUES (NEW.number_school_building, parallel_value, trim(regexp_replace(NEW.class_name, '^\s*МГ:', '')), 'NORMAL',
                    CASE WHEN TG_TABLE_NAME = 'curriculum_plan_entry' THEN NEW.study_period_setting_id ELSE NULL END)
            RETURNING id, name, number_school_building, parallel
            INTO mid, mname, bcode, parallel_value;
        END IF;

        NEW.meta_group_id := mid;
        NEW.class_name := 'МГ:' || mname;
        NEW.number_school_building := bcode;
    ELSE
        SELECT name, number_school_building, parallel
        INTO mname, bcode, parallel_value
        FROM meta_group
        WHERE id = NEW.meta_group_id;

        IF mname IS NULL THEN
            RAISE EXCEPTION 'meta_group_id=% not found for %', NEW.meta_group_id, TG_TABLE_NAME;
        END IF;

        NEW.class_name := 'МГ:' || mname;
        NEW.number_school_building := bcode;
    END IF;

    IF TG_TABLE_NAME = 'curriculum_plan_entry' THEN
        NEW.meta_group := true;
        NEW.class_id := NULL;
    END IF;
    IF TG_TABLE_NAME = 'manual_load_entry' THEN
        NEW.class_id := NULL;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;


CREATE OR REPLACE FUNCTION trg_propagate_meta_group_rename() RETURNS trigger AS $$
BEGIN
    IF coalesce(OLD.name, '') <> coalesce(NEW.name, '')
       OR coalesce(OLD.number_school_building, '') <> coalesce(NEW.number_school_building, '')
       OR coalesce(OLD.study_period_setting_id, -1) <> coalesce(NEW.study_period_setting_id, -1) THEN
        UPDATE curriculum_plan_entry
        SET class_name = 'МГ:' || NEW.name,
            number_school_building = NEW.number_school_building,
            study_period_setting_id = COALESCE(NEW.study_period_setting_id, study_period_setting_id),
            meta_group = true,
            class_id = NULL
        WHERE meta_group_id = NEW.id;

        UPDATE manual_load_entry
        SET class_name = 'МГ:' || NEW.name,
            number_school_building = NEW.number_school_building,
            class_id = NULL
        WHERE meta_group_id = NEW.id;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_meta_group_rename_propagation ON meta_group;
CREATE TRIGGER trg_meta_group_rename_propagation
AFTER UPDATE OF name, number_school_building, study_period_setting_id ON meta_group
FOR EACH ROW EXECUTE FUNCTION trg_propagate_meta_group_rename();

-- Repair data damaged by the old rule: ordinary class members of metagroups
-- regain class_id and lose meta_group_id; explicit МГ rows stay linked to meta_group.
UPDATE curriculum_plan_entry
SET meta_group_id = NULL
WHERE class_name NOT LIKE 'МГ:%'
  AND meta_group_id IS NOT NULL;

UPDATE curriculum_plan_entry
SET class_id = NULL
WHERE class_name LIKE 'МГ:%'
  AND class_id IS NOT NULL;

UPDATE curriculum_plan_entry
SET meta_group_id = meta_group_id
WHERE class_name LIKE 'МГ:%'
  AND meta_group_id IS NULL;

UPDATE curriculum_plan_entry
SET class_id = NULL
WHERE class_name NOT LIKE 'МГ:%'
  AND class_id IS NULL;

ALTER TABLE curriculum_plan_entry
    ADD CONSTRAINT chk_curriculum_class_id_for_regular_class
    CHECK (
        class_name LIKE 'МГ:%'
        OR class_id IS NOT NULL
    );

ALTER TABLE curriculum_plan_entry
    ADD CONSTRAINT chk_curriculum_meta_group_id_for_meta
    CHECK (
        class_name NOT LIKE 'МГ:%'
        OR meta_group_id IS NOT NULL
    );

COMMIT;
