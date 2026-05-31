-- FK-связь предметов с фиксированными 11 предметными областями.
-- subject_catalog_entry.subject_area_id -> subject_area.id.
-- Все новые имена колонок указаны в snake_case; legacy-текст subject_area_name
-- остаётся совместимым полем и синхронизируется триггерами.
-- Миграция идемпотентна: если subject_area_id уже добавлен вручную, корректные
-- FK на одну из 11 областей сохраняются, а legacy-текст синхронизируется по FK.

BEGIN;

-- Старый вариант ограничения был рассчитан на 9 областей, поэтому сначала
-- снимаем его, чтобы можно было безопасно добавить две новые базовые области.
ALTER TABLE subject_area
    DROP CONSTRAINT IF EXISTS chk_subject_area_base_name;

-- 1. Гарантируем наличие 11 фиксированных областей.
WITH base_area(name) AS (
    VALUES
        ('Русский язык и литература'),
        ('Иностранные языки'),
        ('Математика и информатика'),
        ('Общественно-научные предметы'),
        ('Основы духовно-нравственной культуры народов России'),
        ('Естественно-научные предметы'),
        ('Искусство'),
        ('Технология'),
        ('Физическая культура и основы безопасности и защиты Родины'),
        ('Коррекционно-развивающая область'),
        ('Иное')
)
INSERT INTO subject_area(name, created_at)
SELECT name, now()
FROM base_area
ON CONFLICT (name) DO NOTHING;

-- 2. Добавляем FK-колонку и выполняем безопасный backfill.
ALTER TABLE subject_catalog_entry
    ADD COLUMN IF NOT EXISTS subject_area_id BIGINT;

WITH base_area(name) AS (
    VALUES
        ('Русский язык и литература'),
        ('Иностранные языки'),
        ('Математика и информатика'),
        ('Общественно-научные предметы'),
        ('Основы духовно-нравственной культуры народов России'),
        ('Естественно-научные предметы'),
        ('Искусство'),
        ('Технология'),
        ('Физическая культура и основы безопасности и защиты Родины'),
        ('Коррекционно-развивающая область'),
        ('Иное')
), default_area AS (
    SELECT id, name FROM subject_area WHERE name = 'Русский язык и литература' LIMIT 1
), resolved AS (
    SELECT
        s.id AS subject_id,
        COALESCE(area_by_id.id, area_by_name.id, default_area.id) AS resolved_area_id,
        COALESCE(area_by_id.name, area_by_name.name, default_area.name) AS resolved_area_name
    FROM subject_catalog_entry s
    CROSS JOIN default_area
    LEFT JOIN subject_area area_by_id
        ON area_by_id.id = s.subject_area_id
       AND EXISTS (SELECT 1 FROM base_area b WHERE b.name = area_by_id.name)
    LEFT JOIN subject_area area_by_name
        ON lower(trim(area_by_name.name)) = lower(trim(coalesce(nullif(s.subject_area_name, ''), default_area.name)))
       AND EXISTS (SELECT 1 FROM base_area b WHERE b.name = area_by_name.name)
)
UPDATE subject_catalog_entry s
SET subject_area_id = resolved.resolved_area_id,
    subject_area_name = resolved.resolved_area_name
FROM resolved
WHERE s.id = resolved.subject_id
  AND (
      s.subject_area_id IS DISTINCT FROM resolved.resolved_area_id
      OR s.subject_area_name IS DISTINCT FROM resolved.resolved_area_name
  );

ALTER TABLE subject_catalog_entry
    ALTER COLUMN subject_area_id SET NOT NULL;

-- После переназначения предметов удаляем только области вне фиксированного списка из 11.
-- Корректные 11 областей не удаляются и не откатываются обратно к старому списку из 9.
WITH base_area(name) AS (
    VALUES
        ('Русский язык и литература'),
        ('Иностранные языки'),
        ('Математика и информатика'),
        ('Общественно-научные предметы'),
        ('Основы духовно-нравственной культуры народов России'),
        ('Естественно-научные предметы'),
        ('Искусство'),
        ('Технология'),
        ('Физическая культура и основы безопасности и защиты Родины'),
        ('Коррекционно-развивающая область'),
        ('Иное')
)
DELETE FROM subject_area a
WHERE NOT EXISTS (SELECT 1 FROM base_area b WHERE b.name = a.name);

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_subject_catalog_area') THEN
        ALTER TABLE subject_catalog_entry
            ADD CONSTRAINT fk_subject_catalog_area
            FOREIGN KEY (subject_area_id) REFERENCES subject_area(id) ON UPDATE RESTRICT ON DELETE RESTRICT;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_subject_catalog_area_id ON subject_catalog_entry(subject_area_id);

ALTER TABLE subject_area
    ADD CONSTRAINT chk_subject_area_base_name
    CHECK (name IN (
        'Русский язык и литература',
        'Иностранные языки',
        'Математика и информатика',
        'Общественно-научные предметы',
        'Основы духовно-нравственной культуры народов России',
        'Естественно-научные предметы',
        'Искусство',
        'Технология',
        'Физическая культура и основы безопасности и защиты Родины',
        'Коррекционно-развивающая область',
        'Иное'
    ));

-- 3. Синхронизация subject_area_id <-> subject_area_name при insert/update предмета.
CREATE OR REPLACE FUNCTION trg_sync_subject_area_fk() RETURNS trigger AS $$
DECLARE
    aid BIGINT;
    aname TEXT;
BEGIN
    IF NEW.subject_area_id IS NULL
       OR (TG_OP = 'UPDATE'
           AND coalesce(NEW.subject_area_name, '') <> coalesce(OLD.subject_area_name, '')
           AND NEW.subject_area_id IS NOT DISTINCT FROM OLD.subject_area_id) THEN
        SELECT id, name INTO aid, aname
        FROM subject_area
        WHERE lower(trim(name)) = lower(trim(coalesce(nullif(NEW.subject_area_name, ''), '')))
        ORDER BY id
        LIMIT 1;

        IF aid IS NULL THEN
            RAISE EXCEPTION 'subject_area_name must be one of 11 subject areas: %', NEW.subject_area_name;
        END IF;

        NEW.subject_area_id := aid;
        NEW.subject_area_name := aname;
    ELSE
        SELECT name INTO aname FROM subject_area WHERE id = NEW.subject_area_id;
        IF aname IS NULL THEN
            RAISE EXCEPTION 'subject_area_id=% not found for subject_catalog_entry', NEW.subject_area_id;
        END IF;
        NEW.subject_area_name := aname;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_subject_catalog_sync_area_fk ON subject_catalog_entry;
CREATE TRIGGER trg_subject_catalog_sync_area_fk
BEFORE INSERT OR UPDATE ON subject_catalog_entry
FOR EACH ROW EXECUTE FUNCTION trg_sync_subject_area_fk();

-- 4. Переименование фиксированных областей запрещено на уровне API, но если имя изменено SQL-ом,
-- текстовое поле предмета синхронизируется по FK.
CREATE OR REPLACE FUNCTION trg_propagate_subject_area_rename() RETURNS trigger AS $$
BEGIN
    IF coalesce(OLD.name, '') <> coalesce(NEW.name, '') THEN
        UPDATE subject_catalog_entry
        SET subject_area_name = NEW.name
        WHERE subject_area_id = NEW.id;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_subject_area_rename_propagation ON subject_area;
CREATE TRIGGER trg_subject_area_rename_propagation
AFTER UPDATE OF name ON subject_area
FOR EACH ROW EXECUTE FUNCTION trg_propagate_subject_area_rename();

COMMIT;
