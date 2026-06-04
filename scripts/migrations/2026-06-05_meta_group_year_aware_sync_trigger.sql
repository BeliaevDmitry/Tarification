BEGIN;

CREATE OR REPLACE FUNCTION trg_sync_meta_group_fk() RETURNS trigger AS $$
DECLARE
    mid BIGINT;
    mname TEXT;
    bcode TEXT;
    myear TEXT;
BEGIN
    IF NEW.class_name NOT LIKE 'МГ:%' THEN
        NEW.meta_group_id := NULL;
        RETURN NEW;
    END IF;

    IF coalesce(trim(NEW.academic_year), '') = '' THEN
        RAISE EXCEPTION 'academic_year is required for explicit meta group row in %', TG_TABLE_NAME;
    END IF;

    IF NEW.meta_group_id IS NULL THEN
        IF coalesce(trim(regexp_replace(NEW.class_name, '^\s*МГ:', '')), '') = '' THEN
            RAISE EXCEPTION 'meta group class_name is required for %', TG_TABLE_NAME;
        END IF;

        SELECT id, name, number_school_building, academic_year
        INTO mid, mname, bcode, myear
        FROM meta_group
        WHERE academic_year = NEW.academic_year
          AND lower(trim(number_school_building)) = lower(trim(NEW.number_school_building))
          AND lower(trim(name)) = lower(trim(regexp_replace(NEW.class_name, '^\s*МГ:', '')))
        ORDER BY id
        LIMIT 1;

        IF mid IS NULL THEN
            RAISE EXCEPTION
                'Meta group not found for academic_year=%, building=%, name=%. Create the meta group with a physical school building before saving curriculum/manual-load rows.',
                NEW.academic_year,
                NEW.number_school_building,
                trim(regexp_replace(NEW.class_name, '^\s*МГ:', ''));
        END IF;

        NEW.meta_group_id := mid;
        NEW.class_name := 'МГ:' || mname;
        NEW.number_school_building := bcode;
    ELSE
        SELECT name, number_school_building, academic_year
        INTO mname, bcode, myear
        FROM meta_group
        WHERE id = NEW.meta_group_id;

        IF mname IS NULL THEN
            RAISE EXCEPTION 'meta_group_id=% not found for %', NEW.meta_group_id, TG_TABLE_NAME;
        END IF;

        IF myear IS DISTINCT FROM NEW.academic_year THEN
            RAISE EXCEPTION 'meta_group_id=% belongs to academic_year %, but row academic_year is %', NEW.meta_group_id, myear, NEW.academic_year;
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

COMMIT;
