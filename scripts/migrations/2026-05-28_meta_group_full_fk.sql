-- Полноценная FK-схема для метагрупп.
-- Цель: строки учебного плана с metaGroup=true / className='МГ:...' и исторические строки нагрузки
-- с className='МГ:...' ссылаются на meta_group.id. Переименование/перенос метагруппы
-- в справочнике автоматически протягивается в зависимые таблицы.

BEGIN;

-- 1. Добавляем meta_group_id в зависимые таблицы.
ALTER TABLE curriculum_plan_entry
    ADD COLUMN IF NOT EXISTS meta_group_id BIGINT;

ALTER TABLE manual_load_entry
    ADD COLUMN IF NOT EXISTS meta_group_id BIGINT;

-- 2. Backfill meta_group_id по numberSchoolBuilding + className ('МГ:' + meta_group.name).
WITH chosen AS (
    SELECT c.id AS curriculum_id,
           mg.id AS meta_group_id,
           row_number() OVER (PARTITION BY c.id ORDER BY mg.id) AS rn
    FROM curriculum_plan_entry c
    JOIN meta_group mg
      ON lower(trim(c."numberSchoolBuilding")) = lower(trim(mg."numberSchoolBuilding"))
     AND lower(trim(regexp_replace(c."className", '^\s*МГ:', ''))) = lower(trim(mg."name"))
    WHERE c.meta_group_id IS NULL
      AND (coalesce(c."metaGroup", false) = true OR c."className" LIKE 'МГ:%')
)
UPDATE curriculum_plan_entry c
SET meta_group_id = chosen.meta_group_id,
    "className" = 'МГ:' || mg."name",
    "numberSchoolBuilding" = mg."numberSchoolBuilding",
    "studyPeriodSettingId" = COALESCE(c."studyPeriodSettingId", mg."studyPeriodSettingId"),
    "metaGroup" = true,
    class_id = NULL
FROM chosen
JOIN meta_group mg ON mg.id = chosen.meta_group_id
WHERE c.id = chosen.curriculum_id
  AND chosen.rn = 1;

WITH chosen AS (
    SELECT m.id AS manual_id,
           mg.id AS meta_group_id,
           row_number() OVER (PARTITION BY m.id ORDER BY mg.id) AS rn
    FROM manual_load_entry m
    JOIN meta_group mg
      ON lower(trim(m."numberSchoolBuilding")) = lower(trim(mg."numberSchoolBuilding"))
     AND lower(trim(regexp_replace(m."className", '^\s*МГ:', ''))) = lower(trim(mg."name"))
    WHERE m.meta_group_id IS NULL
      AND m."className" LIKE 'МГ:%'
)
UPDATE manual_load_entry m
SET meta_group_id = chosen.meta_group_id,
    "className" = 'МГ:' || mg."name",
    "numberSchoolBuilding" = mg."numberSchoolBuilding",
    class_id = NULL
FROM chosen
JOIN meta_group mg ON mg.id = chosen.meta_group_id
WHERE m.id = chosen.manual_id
  AND chosen.rn = 1;

-- 3. Создаём отсутствующие метагруппы из исторических строк учебного плана.
INSERT INTO meta_group("numberSchoolBuilding", "parallel", "name", "classType", "studyPeriodSettingId")
SELECT DISTINCT
       c."numberSchoolBuilding",
       greatest(1, least(11, coalesce(nullif(substring(regexp_replace(regexp_replace(c."className", '^\s*МГ:', ''), '^\s+', '') from '^(\d{1,2})'), '')::int, 1))),
       trim(regexp_replace(c."className", '^\s*МГ:', '')),
       'NORMAL',
       c."studyPeriodSettingId"
FROM curriculum_plan_entry c
WHERE c.meta_group_id IS NULL
  AND (coalesce(c."metaGroup", false) = true OR c."className" LIKE 'МГ:%')
  AND coalesce(trim(regexp_replace(c."className", '^\s*МГ:', '')), '') <> ''
ON CONFLICT ON CONSTRAINT uk_meta_group_scope DO NOTHING;

-- 4. Создаём отсутствующие метагруппы из нагрузки (на случай исторических данных).
INSERT INTO meta_group("numberSchoolBuilding", "parallel", "name", "classType", "studyPeriodSettingId")
SELECT DISTINCT
       m."numberSchoolBuilding",
       greatest(1, least(11, coalesce(nullif(substring(regexp_replace(regexp_replace(m."className", '^\s*МГ:', ''), '^\s+', '') from '^(\d{1,2})'), '')::int, 1))),
       trim(regexp_replace(m."className", '^\s*МГ:', '')),
       'NORMAL',
       NULL
FROM manual_load_entry m
WHERE m.meta_group_id IS NULL
  AND m."className" LIKE 'МГ:%'
  AND coalesce(trim(regexp_replace(m."className", '^\s*МГ:', '')), '') <> ''
ON CONFLICT ON CONSTRAINT uk_meta_group_scope DO NOTHING;

-- 5. Повторный backfill после автосоздания.
WITH chosen AS (
    SELECT c.id AS curriculum_id,
           mg.id AS meta_group_id,
           row_number() OVER (PARTITION BY c.id ORDER BY mg.id) AS rn
    FROM curriculum_plan_entry c
    JOIN meta_group mg
      ON lower(trim(c."numberSchoolBuilding")) = lower(trim(mg."numberSchoolBuilding"))
     AND lower(trim(regexp_replace(c."className", '^\s*МГ:', ''))) = lower(trim(mg."name"))
    WHERE c.meta_group_id IS NULL
      AND (coalesce(c."metaGroup", false) = true OR c."className" LIKE 'МГ:%')
)
UPDATE curriculum_plan_entry c
SET meta_group_id = chosen.meta_group_id,
    "className" = 'МГ:' || mg."name",
    "numberSchoolBuilding" = mg."numberSchoolBuilding",
    "studyPeriodSettingId" = COALESCE(c."studyPeriodSettingId", mg."studyPeriodSettingId"),
    "metaGroup" = true,
    class_id = NULL
FROM chosen
JOIN meta_group mg ON mg.id = chosen.meta_group_id
WHERE c.id = chosen.curriculum_id
  AND chosen.rn = 1;

WITH chosen AS (
    SELECT m.id AS manual_id,
           mg.id AS meta_group_id,
           row_number() OVER (PARTITION BY m.id ORDER BY mg.id) AS rn
    FROM manual_load_entry m
    JOIN meta_group mg
      ON lower(trim(m."numberSchoolBuilding")) = lower(trim(mg."numberSchoolBuilding"))
     AND lower(trim(regexp_replace(m."className", '^\s*МГ:', ''))) = lower(trim(mg."name"))
    WHERE m.meta_group_id IS NULL
      AND m."className" LIKE 'МГ:%'
)
UPDATE manual_load_entry m
SET meta_group_id = chosen.meta_group_id,
    "className" = 'МГ:' || mg."name",
    "numberSchoolBuilding" = mg."numberSchoolBuilding",
    class_id = NULL
FROM chosen
JOIN meta_group mg ON mg.id = chosen.meta_group_id
WHERE m.id = chosen.manual_id
  AND chosen.rn = 1;

-- 6. FK + индексы.
ALTER TABLE curriculum_plan_entry
    ADD CONSTRAINT fk_curriculum_meta_group
    FOREIGN KEY (meta_group_id) REFERENCES meta_group(id) ON UPDATE RESTRICT ON DELETE RESTRICT;
CREATE INDEX IF NOT EXISTS idx_curriculum_meta_group_id ON curriculum_plan_entry(meta_group_id);

ALTER TABLE manual_load_entry
    ADD CONSTRAINT fk_manual_meta_group
    FOREIGN KEY (meta_group_id) REFERENCES meta_group(id) ON UPDATE RESTRICT ON DELETE RESTRICT;
CREATE INDEX IF NOT EXISTS idx_manual_meta_group_id ON manual_load_entry(meta_group_id);

-- Для строк-метагрупп meta_group_id обязателен. Для обычных классов NULL допустим.
ALTER TABLE curriculum_plan_entry
    ADD CONSTRAINT chk_curriculum_meta_group_id_for_meta
    CHECK ((coalesce("metaGroup", false) = false AND "className" NOT LIKE 'МГ:%') OR meta_group_id IS NOT NULL);

ALTER TABLE manual_load_entry
    ADD CONSTRAINT chk_manual_meta_group_id_for_meta
    CHECK ("className" NOT LIKE 'МГ:%' OR meta_group_id IS NOT NULL);

-- 7. Синхронизация meta_group_id <-> className/numberSchoolBuilding для insert/update.
CREATE OR REPLACE FUNCTION trg_sync_meta_group_fk() RETURNS trigger AS $$
DECLARE
    mid BIGINT;
    mname TEXT;
    bcode TEXT;
    parallel_value INTEGER;
BEGIN
    IF TG_TABLE_NAME = 'curriculum_plan_entry'
       AND coalesce(NEW."metaGroup", false) = false
       AND NEW."className" NOT LIKE 'МГ:%' THEN
        NEW.meta_group_id := NULL;
        RETURN NEW;
    END IF;

    IF TG_TABLE_NAME = 'manual_load_entry'
       AND NEW."className" NOT LIKE 'МГ:%' THEN
        NEW.meta_group_id := NULL;
        RETURN NEW;
    END IF;

    IF NEW.meta_group_id IS NULL THEN
        IF coalesce(trim(regexp_replace(NEW."className", '^\s*МГ:', '')), '') = '' THEN
            RAISE EXCEPTION 'meta group className is required for %', TG_TABLE_NAME;
        END IF;

        SELECT id, "name", "numberSchoolBuilding", "parallel"
        INTO mid, mname, bcode, parallel_value
        FROM meta_group
        WHERE lower(trim("numberSchoolBuilding")) = lower(trim(NEW."numberSchoolBuilding"))
          AND lower(trim("name")) = lower(trim(regexp_replace(NEW."className", '^\s*МГ:', '')))
        ORDER BY id
        LIMIT 1;

        IF mid IS NULL THEN
            parallel_value := greatest(1, least(11, coalesce(nullif(substring(regexp_replace(regexp_replace(NEW."className", '^\s*МГ:', ''), '^\s+', '') from '^(\d{1,2})'), '')::int, 1)));
            INSERT INTO meta_group("numberSchoolBuilding", "parallel", "name", "classType", "studyPeriodSettingId")
            VALUES (NEW."numberSchoolBuilding", parallel_value, trim(regexp_replace(NEW."className", '^\s*МГ:', '')), 'NORMAL',
                    CASE WHEN TG_TABLE_NAME = 'curriculum_plan_entry' THEN NEW."studyPeriodSettingId" ELSE NULL END)
            RETURNING id, "name", "numberSchoolBuilding", "parallel"
            INTO mid, mname, bcode, parallel_value;
        END IF;

        NEW.meta_group_id := mid;
        NEW."className" := 'МГ:' || mname;
        NEW."numberSchoolBuilding" := bcode;
    ELSE
        SELECT "name", "numberSchoolBuilding", "parallel"
        INTO mname, bcode, parallel_value
        FROM meta_group
        WHERE id = NEW.meta_group_id;

        IF mname IS NULL THEN
            RAISE EXCEPTION 'meta_group_id=% not found for %', NEW.meta_group_id, TG_TABLE_NAME;
        END IF;

        NEW."className" := 'МГ:' || mname;
        NEW."numberSchoolBuilding" := bcode;
    END IF;

    IF TG_TABLE_NAME = 'curriculum_plan_entry' THEN
        NEW."metaGroup" := true;
        NEW.class_id := NULL;
    END IF;
    IF TG_TABLE_NAME = 'manual_load_entry' THEN
        NEW.class_id := NULL;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_curriculum_sync_meta_group_fk ON curriculum_plan_entry;
CREATE TRIGGER trg_curriculum_sync_meta_group_fk
BEFORE INSERT OR UPDATE ON curriculum_plan_entry
FOR EACH ROW EXECUTE FUNCTION trg_sync_meta_group_fk();

DROP TRIGGER IF EXISTS trg_manual_sync_meta_group_fk ON manual_load_entry;
CREATE TRIGGER trg_manual_sync_meta_group_fk
BEFORE INSERT OR UPDATE ON manual_load_entry
FOR EACH ROW EXECUTE FUNCTION trg_sync_meta_group_fk();

-- 8. Протяжка переименования/переноса метагруппы из справочника «Метагруппы».
CREATE OR REPLACE FUNCTION trg_propagate_meta_group_rename() RETURNS trigger AS $$
BEGIN
    IF coalesce(OLD."name", '') <> coalesce(NEW."name", '')
       OR coalesce(OLD."numberSchoolBuilding", '') <> coalesce(NEW."numberSchoolBuilding", '')
       OR coalesce(OLD."studyPeriodSettingId", -1) <> coalesce(NEW."studyPeriodSettingId", -1) THEN
        UPDATE curriculum_plan_entry
        SET "className" = 'МГ:' || NEW."name",
            "numberSchoolBuilding" = NEW."numberSchoolBuilding",
            "studyPeriodSettingId" = COALESCE(NEW."studyPeriodSettingId", "studyPeriodSettingId"),
            "metaGroup" = true,
            class_id = NULL
        WHERE meta_group_id = NEW.id;

        UPDATE manual_load_entry
        SET "className" = 'МГ:' || NEW."name",
            "numberSchoolBuilding" = NEW."numberSchoolBuilding",
            class_id = NULL
        WHERE meta_group_id = NEW.id;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_meta_group_rename_propagation ON meta_group;
CREATE TRIGGER trg_meta_group_rename_propagation
AFTER UPDATE OF "name", "numberSchoolBuilding", "studyPeriodSettingId" ON meta_group
FOR EACH ROW EXECUTE FUNCTION trg_propagate_meta_group_rename();

COMMIT;
