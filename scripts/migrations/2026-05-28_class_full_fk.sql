-- Полноценная FK-схема для классов.
-- Цель: обычные классы в curriculum/manual ссылаются на classroom_leadership_entry.id,
-- а переименование класса в справочнике «Классы» автоматически протягивается в учебный план и нагрузку.
-- Метагруппы (className = 'МГ:...' или metaGroup=true) остаются без class_id, так как живут в отдельном справочнике meta_group.

BEGIN;

-- 1. Добавляем class_id в зависимые таблицы.
ALTER TABLE curriculum_plan_entry
    ADD COLUMN IF NOT EXISTS class_id BIGINT;

ALTER TABLE manual_load_entry
    ADD COLUMN IF NOT EXISTS class_id BIGINT;

-- 2. Backfill для учебного плана: обычные классы ищем по academicYear + className.
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

-- 3. Backfill для нагрузки: обычные классы ищем по academicYear + className.
UPDATE manual_load_entry m
SET class_id = cl.id,
    "className" = cl."className",
    "numberSchoolBuilding" = cl."numberSchoolBuilding"
FROM classroom_leadership_entry cl
WHERE m.class_id IS NULL
  AND m."className" NOT LIKE 'МГ:%'
  AND coalesce(m."academicYear", '') = coalesce(cl."academicYear", '')
  AND lower(trim(m."className")) = lower(trim(cl."className"));

-- 4. Создаём отсутствующие классы из обычных строк учебного плана.
-- Это нужно, чтобы исторические данные не мешали включить FK.
INSERT INTO classroom_leadership_entry(
    "academicYear",
    "numberSchoolBuilding",
    "className",
    "classDirection",
    "fioTeacher",
    "campusAddress",
    "classType",
    "createdAt"
)
SELECT DISTINCT
       coalesce(c."academicYear", ''),
       c."numberSchoolBuilding",
       trim(c."className"),
       'Не указана',
       'Вакансия',
       '',
       'NORMAL',
       now()
FROM curriculum_plan_entry c
WHERE c.class_id IS NULL
  AND coalesce(c."metaGroup", false) = false
  AND c."className" NOT LIKE 'МГ:%'
  AND coalesce(trim(c."className"), '') <> ''
ON CONFLICT ON CONSTRAINT uk_classroom_leadership_class DO NOTHING;

-- 5. Создаём отсутствующие классы из нагрузки.
INSERT INTO classroom_leadership_entry(
    "academicYear",
    "numberSchoolBuilding",
    "className",
    "classDirection",
    "fioTeacher",
    "campusAddress",
    "classType",
    "createdAt"
)
SELECT DISTINCT
       coalesce(m."academicYear", ''),
       m."numberSchoolBuilding",
       trim(m."className"),
       'Не указана',
       'Вакансия',
       '',
       'NORMAL',
       now()
FROM manual_load_entry m
WHERE m.class_id IS NULL
  AND m."className" NOT LIKE 'МГ:%'
  AND coalesce(trim(m."className"), '') <> ''
ON CONFLICT ON CONSTRAINT uk_classroom_leadership_class DO NOTHING;

-- 6. Повторный backfill после автосоздания.
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

-- 7. FK + индексы.
ALTER TABLE curriculum_plan_entry
    ADD CONSTRAINT fk_curriculum_class
    FOREIGN KEY (class_id) REFERENCES classroom_leadership_entry(id) ON UPDATE RESTRICT ON DELETE RESTRICT;
CREATE INDEX IF NOT EXISTS idx_curriculum_class_id ON curriculum_plan_entry(class_id);

ALTER TABLE manual_load_entry
    ADD CONSTRAINT fk_manual_class
    FOREIGN KEY (class_id) REFERENCES classroom_leadership_entry(id) ON UPDATE RESTRICT ON DELETE RESTRICT;
CREATE INDEX IF NOT EXISTS idx_manual_class_id ON manual_load_entry(class_id);

-- Для обычных классов class_id обязателен. Для метагрупп — NULL допустим.
ALTER TABLE curriculum_plan_entry
    ADD CONSTRAINT chk_curriculum_class_id_for_regular_class
    CHECK (coalesce("metaGroup", false) = true OR "className" LIKE 'МГ:%' OR class_id IS NOT NULL);

ALTER TABLE manual_load_entry
    ADD CONSTRAINT chk_manual_class_id_required
    CHECK ("className" LIKE 'МГ:%' OR class_id IS NOT NULL);

-- 8. Синхронизация class_id <-> className/numberSchoolBuilding для insert/update.
CREATE OR REPLACE FUNCTION trg_sync_class_fk() RETURNS trigger AS $$
DECLARE
    cid BIGINT;
    cname TEXT;
    bcode TEXT;
    ctype TEXT;
BEGIN
    -- Метагруппы не являются обычными классами и обслуживаются meta_group.
    IF TG_TABLE_NAME = 'curriculum_plan_entry'
       AND (coalesce(NEW."metaGroup", false) = true OR NEW."className" LIKE 'МГ:%') THEN
        NEW.class_id := NULL;
        RETURN NEW;
    END IF;

    IF NEW."className" LIKE 'МГ:%' THEN
        NEW.class_id := NULL;
        RETURN NEW;
    END IF;

    IF NEW.class_id IS NULL THEN
        IF coalesce(trim(NEW."className"), '') = '' THEN
            RAISE EXCEPTION 'className is required for %', TG_TABLE_NAME;
        END IF;

        SELECT id, "className", "numberSchoolBuilding", "classType"
        INTO cid, cname, bcode, ctype
        FROM classroom_leadership_entry
        WHERE coalesce("academicYear", '') = coalesce(NEW."academicYear", '')
          AND lower(trim("className")) = lower(trim(NEW."className"))
        ORDER BY id
        LIMIT 1;

        IF cid IS NULL THEN
            INSERT INTO classroom_leadership_entry(
                "academicYear",
                "numberSchoolBuilding",
                "className",
                "classDirection",
                "fioTeacher",
                "campusAddress",
                "classType",
                "createdAt"
            ) VALUES (
                coalesce(NEW."academicYear", ''),
                NEW."numberSchoolBuilding",
                trim(NEW."className"),
                'Не указана',
                'Вакансия',
                '',
                'NORMAL',
                now()
            )
            RETURNING id, "className", "numberSchoolBuilding", "classType"
            INTO cid, cname, bcode, ctype;
        END IF;

        NEW.class_id := cid;
        NEW."className" := cname;
        NEW."numberSchoolBuilding" := bcode;
    ELSE
        SELECT "className", "numberSchoolBuilding", "classType"
        INTO cname, bcode, ctype
        FROM classroom_leadership_entry
        WHERE id = NEW.class_id;

        IF cname IS NULL THEN
            RAISE EXCEPTION 'class_id=% not found for %', NEW.class_id, TG_TABLE_NAME;
        END IF;

        NEW."className" := cname;
        NEW."numberSchoolBuilding" := bcode;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_curriculum_sync_class_fk ON curriculum_plan_entry;
CREATE TRIGGER trg_curriculum_sync_class_fk
BEFORE INSERT OR UPDATE ON curriculum_plan_entry
FOR EACH ROW EXECUTE FUNCTION trg_sync_class_fk();

DROP TRIGGER IF EXISTS trg_manual_sync_class_fk ON manual_load_entry;
CREATE TRIGGER trg_manual_sync_class_fk
BEFORE INSERT OR UPDATE ON manual_load_entry
FOR EACH ROW EXECUTE FUNCTION trg_sync_class_fk();

-- 9. Протяжка переименования/переноса класса из справочника «Классы».
CREATE OR REPLACE FUNCTION trg_propagate_class_rename() RETURNS trigger AS $$
BEGIN
    IF coalesce(OLD."className", '') <> coalesce(NEW."className", '')
       OR coalesce(OLD."numberSchoolBuilding", '') <> coalesce(NEW."numberSchoolBuilding", '') THEN
        UPDATE curriculum_plan_entry
        SET "className" = NEW."className",
            "numberSchoolBuilding" = NEW."numberSchoolBuilding"
        WHERE class_id = NEW.id;

        UPDATE manual_load_entry
        SET "className" = NEW."className",
            "numberSchoolBuilding" = NEW."numberSchoolBuilding"
        WHERE class_id = NEW.id;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_class_rename_propagation ON classroom_leadership_entry;
CREATE TRIGGER trg_class_rename_propagation
AFTER UPDATE OF "className", "numberSchoolBuilding" ON classroom_leadership_entry
FOR EACH ROW EXECUTE FUNCTION trg_propagate_class_rename();

COMMIT;
