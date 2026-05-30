-- Усиление FK-синхронизации классов после перехода на class_id.
-- Legacy-строки без class_id привязываются с учётом корпуса, чтобы одинаковые
-- class_name в разных number_school_building не склеивались.

BEGIN;

UPDATE curriculum_plan_entry c
SET class_id = cl.id,
    class_name = cl.class_name,
    number_school_building = cl.number_school_building
FROM classroom_leadership_entry cl
WHERE (c.class_id IS NULL OR c.class_id <> cl.id)
  AND coalesce(c.meta_group, false) = false
  AND c.class_name NOT LIKE 'МГ:%'
  AND coalesce(c.academic_year, '') = coalesce(cl.academic_year, '')
  AND upper(replace(split_part(coalesce(c.number_school_building, ''), '|', 1), ' ', '')) = upper(replace(split_part(coalesce(cl.number_school_building, ''), '|', 1), ' ', ''))
  AND lower(trim(c.class_name)) = lower(trim(cl.class_name));

UPDATE manual_load_entry m
SET class_id = cl.id,
    class_name = cl.class_name,
    number_school_building = cl.number_school_building
FROM classroom_leadership_entry cl
WHERE (m.class_id IS NULL OR m.class_id <> cl.id)
  AND m.class_name NOT LIKE 'МГ:%'
  AND coalesce(m.academic_year, '') = coalesce(cl.academic_year, '')
  AND upper(replace(split_part(coalesce(m.number_school_building, ''), '|', 1), ' ', '')) = upper(replace(split_part(coalesce(cl.number_school_building, ''), '|', 1), ' ', ''))
  AND lower(trim(m.class_name)) = lower(trim(cl.class_name));

CREATE OR REPLACE FUNCTION trg_propagate_class_rename() RETURNS trigger AS $$
BEGIN
    IF coalesce(OLD.class_name, '') <> coalesce(NEW.class_name, '')
       OR coalesce(OLD.number_school_building, '') <> coalesce(NEW.number_school_building, '') THEN
        UPDATE curriculum_plan_entry
        SET class_name = NEW.class_name,
            number_school_building = NEW.number_school_building,
            class_id = NEW.id
        WHERE class_id = NEW.id
           OR (
                class_id IS NULL
                AND coalesce(academic_year, '') = coalesce(NEW.academic_year, '')
                AND upper(replace(split_part(coalesce(number_school_building, ''), '|', 1), ' ', '')) = upper(replace(split_part(coalesce(OLD.number_school_building, ''), '|', 1), ' ', ''))
                AND lower(trim(class_name)) = lower(trim(OLD.class_name))
           );

        UPDATE manual_load_entry
        SET class_name = NEW.class_name,
            number_school_building = NEW.number_school_building,
            class_id = NEW.id
        WHERE class_id = NEW.id
           OR (
                class_id IS NULL
                AND coalesce(academic_year, '') = coalesce(NEW.academic_year, '')
                AND upper(replace(split_part(coalesce(number_school_building, ''), '|', 1), ' ', '')) = upper(replace(split_part(coalesce(OLD.number_school_building, ''), '|', 1), ' ', ''))
                AND lower(trim(class_name)) = lower(trim(OLD.class_name))
           );
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_class_rename_propagation ON classroom_leadership_entry;
CREATE TRIGGER trg_class_rename_propagation
AFTER UPDATE OF class_name, number_school_building ON classroom_leadership_entry
FOR EACH ROW EXECUTE FUNCTION trg_propagate_class_rename();

COMMIT;
