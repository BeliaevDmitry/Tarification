-- FK-связь предметов с фиксированными 9 базовыми предметными областями.
-- subject_catalog_entry.subject_area_id -> subject_area.id.
-- Все новые имена колонок указаны в snake_case; legacy-текст subject_area_name
-- остаётся совместимым полем и синхронизируется триггерами.

BEGIN;

-- 1. Гарантируем наличие только базовых областей.
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
        ('Физическая культура и основы безопасности и защиты Родины')
)
INSERT INTO subject_area(name, created_at)
SELECT name, now()
FROM base_area
ON CONFLICT (name) DO NOTHING;

-- 2. Добавляем FK-колонку и выполняем backfill.
ALTER TABLE subject_catalog_entry
    ADD COLUMN IF NOT EXISTS subject_area_id BIGINT;

WITH default_area AS (
    SELECT id, name FROM subject_area WHERE name = 'Русский язык и литература' LIMIT 1
)
UPDATE subject_catalog_entry s
SET subject_area_id = coalesce((
        SELECT exact_area.id
        FROM subject_area exact_area
        WHERE lower(trim(exact_area.name)) = lower(trim(coalesce(nullif(s.subject_area_name, ''), default_area.name)))
          AND exact_area.name IN (
              'Русский язык и литература',
              'Иностранные языки',
              'Математика и информатика',
              'Общественно-научные предметы',
              'Основы духовно-нравственной культуры народов России',
              'Естественно-научные предметы',
              'Искусство',
              'Технология',
              'Физическая культура и основы безопасности и защиты Родины'
          )
        LIMIT 1
    ), default_area.id),
    subject_area_name = coalesce((
        SELECT exact_area.name
        FROM subject_area exact_area
        WHERE lower(trim(exact_area.name)) = lower(trim(coalesce(nullif(s.subject_area_name, ''), default_area.name)))
          AND exact_area.name IN (
              'Русский язык и литература',
              'Иностранные языки',
              'Математика и информатика',
              'Общественно-научные предметы',
              'Основы духовно-нравственной культуры народов России',
              'Естественно-научные предметы',
              'Искусство',
              'Технология',
              'Физическая культура и основы безопасности и защиты Родины'
          )
        LIMIT 1
    ), default_area.name)
FROM default_area
WHERE s.subject_area_id IS NULL
   OR s.subject_area_name IS DISTINCT FROM coalesce((
        SELECT exact_area.name
        FROM subject_area exact_area
        WHERE lower(trim(exact_area.name)) = lower(trim(coalesce(nullif(s.subject_area_name, ''), default_area.name)))
          AND exact_area.name IN (
              'Русский язык и литература',
              'Иностранные языки',
              'Математика и информатика',
              'Общественно-научные предметы',
              'Основы духовно-нравственной культуры народов России',
              'Естественно-научные предметы',
              'Искусство',
              'Технология',
              'Физическая культура и основы безопасности и защиты Родины'
          )
        LIMIT 1
    ), default_area.name);

ALTER TABLE subject_catalog_entry
    ALTER COLUMN subject_area_id SET NOT NULL;

-- После переназначения предметов удаляем небазовые области, чтобы справочник был ровно из 9 вариантов.
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
        ('Физическая культура и основы безопасности и защиты Родины')
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

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_subject_area_base_name') THEN
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
                'Физическая культура и основы безопасности и защиты Родины'
            ));
    END IF;
END $$;

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
            RAISE EXCEPTION 'subject_area_name must be one of 9 base subject areas: %', NEW.subject_area_name;
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

-- 4. Переименование базовых областей запрещено на уровне API, но если имя изменено SQL-ом,
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
