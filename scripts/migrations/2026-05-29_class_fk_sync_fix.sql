-- Усиление FK-синхронизации классов после перехода на class_id.
-- Чинит два сценария:
-- 1) старые строки без class_id повторно привязываются к справочнику классов;
-- 2) переименование класса протягивается не только по class_id, но и по старому имени,
--    если в базе ещё остались legacy-строки без class_id.

BEGIN;

UPDATE curriculum_plan_entry c
SET class_id = cl.id,
    "className" = cl."className",
    "numberSchoolBuilding" = cl."numberSchoolBuilding"
FROM classroom_leadership_entry cl
WHERE c.class_id IS NULL
  AND coalesce(c."metaGroup", false) = false
  AND c."className" NOT LIKE 'МГ:%'
  AND coalesce(c."academicYear", '') = coalesce(cl."academicYear", '')
  AND lower(trim(c."className")) = lower(trim(cl."className"));

UPDATE manual_load_entry m
SET class_id = cl.id,
    "className" = cl."className",
    "numberSchoolBuilding" = cl."numberSchoolBuilding"
FROM classroom_leadership_entry cl
WHERE m.class_id IS NULL
  AND m."className" NOT LIKE 'МГ:%'
  AND coalesce(m."academicYear", '') = coalesce(cl."academicYear", '')
  AND lower(trim(m."className")) = lower(trim(cl."className"));

CREATE OR REPLACE FUNCTION trg_propagate_class_rename() RETURNS trigger AS $$
BEGIN
    IF coalesce(OLD."className", '') <> coalesce(NEW."className", '')
       OR coalesce(OLD."numberSchoolBuilding", '') <> coalesce(NEW."numberSchoolBuilding", '') THEN
        UPDATE curriculum_plan_entry
        SET "className" = NEW."className",
            "numberSchoolBuilding" = NEW."numberSchoolBuilding",
            class_id = NEW.id
        WHERE class_id = NEW.id
           OR (
                class_id IS NULL
                AND coalesce("academicYear", '') = coalesce(NEW."academicYear", '')
                AND lower(trim("className")) = lower(trim(OLD."className"))
           );

        UPDATE manual_load_entry
        SET "className" = NEW."className",
            "numberSchoolBuilding" = NEW."numberSchoolBuilding",
            class_id = NEW.id
        WHERE class_id = NEW.id
           OR (
                class_id IS NULL
                AND coalesce("academicYear", '') = coalesce(NEW."academicYear", '')
                AND lower(trim("className")) = lower(trim(OLD."className"))
           );
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_class_rename_propagation ON classroom_leadership_entry;
CREATE TRIGGER trg_class_rename_propagation
AFTER UPDATE OF "className", "numberSchoolBuilding" ON classroom_leadership_entry
FOR EACH ROW EXECUTE FUNCTION trg_propagate_class_rename();

COMMIT;
