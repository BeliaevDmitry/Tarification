-- Полноценная FK-схема для классов.
-- Обычные строки учебного плана/нагрузки ссылаются на classroom_leadership_entry.id.
-- Важно: класс ищется в рамках academic_year + number_school_building + class_name,
-- чтобы одинаковые имена классов в разных корпусах (например 7-Б) не склеивались.

BEGIN;

-- 0. Уникальность класса должна учитывать корпус.
ALTER TABLE classroom_leadership_entry
    DROP CONSTRAINT IF EXISTS uk_classroom_leadership_class;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'uk_classroom_leadership_class_building'
    ) THEN
        ALTER TABLE classroom_leadership_entry
            ADD CONSTRAINT uk_classroom_leadership_class_building
            UNIQUE (academic_year, number_school_building, class_name);
    END IF;
END $$;

-- 1. Добавляем class_id в зависимые таблицы.
ALTER TABLE curriculum_plan_entry
    ADD COLUMN IF NOT EXISTS class_id BIGINT;

ALTER TABLE manual_load_entry
    ADD COLUMN IF NOT EXISTS class_id BIGINT;

-- 2. Backfill для учебного плана: обычные классы ищем по academic_year + number_school_building + class_name.
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

-- 3. Backfill для нагрузки: обычные классы ищем по academic_year + number_school_building + class_name.
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

-- 4. Создаём отсутствующие классы из обычных строк учебного плана.
INSERT INTO classroom_leadership_entry(
    academic_year,
    number_school_building,
    class_name,
    class_direction,
    fio_teacher,
    campus_address,
    class_type,
    created_at
)
SELECT DISTINCT
       coalesce(c.academic_year, ''),
       c.number_school_building,
       trim(c.class_name),
       'Не указана',
       'Вакансия',
       '',
       'NORMAL',
       now()
FROM curriculum_plan_entry c
WHERE c.class_id IS NULL
  AND coalesce(c.meta_group, false) = false
  AND c.class_name NOT LIKE 'МГ:%'
  AND coalesce(trim(c.class_name), '') <> ''
  AND coalesce(trim(c.number_school_building), '') <> ''
ON CONFLICT ON CONSTRAINT uk_classroom_leadership_class_building DO NOTHING;

-- 5. Создаём отсутствующие классы из нагрузки.
INSERT INTO classroom_leadership_entry(
    academic_year,
    number_school_building,
    class_name,
    class_direction,
    fio_teacher,
    campus_address,
    class_type,
    created_at
)
SELECT DISTINCT
       coalesce(m.academic_year, ''),
       m.number_school_building,
       trim(m.class_name),
       'Не указана',
       'Вакансия',
       '',
       'NORMAL',
       now()
FROM manual_load_entry m
WHERE m.class_id IS NULL
  AND m.class_name NOT LIKE 'МГ:%'
  AND coalesce(trim(m.class_name), '') <> ''
  AND coalesce(trim(m.number_school_building), '') <> ''
ON CONFLICT ON CONSTRAINT uk_classroom_leadership_class_building DO NOTHING;

-- 6. Повторный backfill после автосоздания.
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

-- 7. FK + индексы. Constraint-ы добавляем безопасно для повторного запуска миграции.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_curriculum_class') THEN
        ALTER TABLE curriculum_plan_entry
            ADD CONSTRAINT fk_curriculum_class
            FOREIGN KEY (class_id) REFERENCES classroom_leadership_entry(id) ON UPDATE RESTRICT ON DELETE RESTRICT;
    END IF;
END $$;
CREATE INDEX IF NOT EXISTS idx_curriculum_class_id ON curriculum_plan_entry(class_id);

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_manual_class') THEN
        ALTER TABLE manual_load_entry
            ADD CONSTRAINT fk_manual_class
            FOREIGN KEY (class_id) REFERENCES classroom_leadership_entry(id) ON UPDATE RESTRICT ON DELETE RESTRICT;
    END IF;
END $$;
CREATE INDEX IF NOT EXISTS idx_manual_class_id ON manual_load_entry(class_id);

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_curriculum_class_id_for_regular_class') THEN
        ALTER TABLE curriculum_plan_entry
            ADD CONSTRAINT chk_curriculum_class_id_for_regular_class
            CHECK (class_name LIKE 'МГ:%' OR class_id IS NOT NULL);
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_manual_class_id_required') THEN
        ALTER TABLE manual_load_entry
            ADD CONSTRAINT chk_manual_class_id_required
            CHECK (class_name LIKE 'МГ:%' OR class_id IS NOT NULL);
    END IF;
END $$;

-- 8. Синхронизация class_id <-> class_name/number_school_building для insert/update.
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
    IF coalesce(OLD.class_name, '') <> coalesce(NEW.class_name, '')
       OR coalesce(OLD.number_school_building, '') <> coalesce(NEW.number_school_building, '') THEN
        UPDATE curriculum_plan_entry
        SET class_name = NEW.class_name,
            number_school_building = NEW.number_school_building
        WHERE class_id = NEW.id;

        UPDATE manual_load_entry
        SET class_name = NEW.class_name,
            number_school_building = NEW.number_school_building
        WHERE class_id = NEW.id;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_class_rename_propagation ON classroom_leadership_entry;
CREATE TRIGGER trg_class_rename_propagation
AFTER UPDATE OF class_name, number_school_building ON classroom_leadership_entry
FOR EACH ROW EXECUTE FUNCTION trg_propagate_class_rename();

COMMIT;
