-- Полноценная FK-связь предметов с предметными областями.
-- subject_catalog_entry.subject_area_id -> subject_area.id, а subjectAreaName остаётся
-- текстовым совместимым полем и синхронизируется триггерами.

BEGIN;

-- 1. Гарантируем наличие областей для уже существующих текстовых значений.
INSERT INTO subject_area(name, "createdAt")
SELECT DISTINCT coalesce(nullif(trim("subjectAreaName"), ''), 'Без области'), now()
FROM subject_catalog_entry
WHERE coalesce(nullif(trim("subjectAreaName"), ''), 'Без области') <> ''
ON CONFLICT (name) DO NOTHING;

INSERT INTO subject_area(name, "createdAt")
VALUES ('Без области', now())
ON CONFLICT (name) DO NOTHING;

-- 2. Добавляем FK-колонку и выполняем backfill.
ALTER TABLE subject_catalog_entry
    ADD COLUMN IF NOT EXISTS subject_area_id BIGINT;

UPDATE subject_catalog_entry s
SET subject_area_id = a.id,
    "subjectAreaName" = a.name
FROM subject_area a
WHERE lower(trim(a.name)) = lower(trim(coalesce(nullif(s."subjectAreaName", ''), 'Без области')));

ALTER TABLE subject_catalog_entry
    ALTER COLUMN subject_area_id SET NOT NULL;

ALTER TABLE subject_catalog_entry
    ADD CONSTRAINT fk_subject_catalog_area
    FOREIGN KEY (subject_area_id) REFERENCES subject_area(id) ON UPDATE RESTRICT ON DELETE RESTRICT;

CREATE INDEX IF NOT EXISTS idx_subject_catalog_area_id ON subject_catalog_entry(subject_area_id);

-- 3. Синхронизация subject_area_id <-> subjectAreaName при insert/update предмета.
CREATE OR REPLACE FUNCTION trg_sync_subject_area_fk() RETURNS trigger AS $$
DECLARE
    aid BIGINT;
    aname TEXT;
BEGIN
    IF NEW.subject_area_id IS NULL
       OR (TG_OP = 'UPDATE'
           AND coalesce(NEW."subjectAreaName", '') <> coalesce(OLD."subjectAreaName", '')
           AND NEW.subject_area_id IS NOT DISTINCT FROM OLD.subject_area_id) THEN
        SELECT id, name INTO aid, aname
        FROM subject_area
        WHERE lower(trim(name)) = lower(trim(coalesce(nullif(NEW."subjectAreaName", ''), 'Без области')))
        ORDER BY id
        LIMIT 1;

        IF aid IS NULL THEN
            INSERT INTO subject_area(name, "createdAt")
            VALUES (coalesce(nullif(trim(NEW."subjectAreaName"), ''), 'Без области'), now())
            RETURNING id, name INTO aid, aname;
        END IF;

        NEW.subject_area_id := aid;
        NEW."subjectAreaName" := aname;
    ELSE
        SELECT name INTO aname FROM subject_area WHERE id = NEW.subject_area_id;
        IF aname IS NULL THEN
            RAISE EXCEPTION 'subject_area_id=% not found for subject_catalog_entry', NEW.subject_area_id;
        END IF;
        NEW."subjectAreaName" := aname;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_subject_catalog_sync_area_fk ON subject_catalog_entry;
CREATE TRIGGER trg_subject_catalog_sync_area_fk
BEFORE INSERT OR UPDATE ON subject_catalog_entry
FOR EACH ROW EXECUTE FUNCTION trg_sync_subject_area_fk();

-- 4. Переименование области протягивается во все предметы.
CREATE OR REPLACE FUNCTION trg_propagate_subject_area_rename() RETURNS trigger AS $$
BEGIN
    IF coalesce(OLD.name, '') <> coalesce(NEW.name, '') THEN
        UPDATE subject_catalog_entry
        SET "subjectAreaName" = NEW.name
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
