-- Полноценная FK-схема для предметов и педагогов
-- Цели:
-- 1) Любое переименование предмета в subject_catalog_entry автоматически отражается в curriculum/manual.
-- 2) Любое переименование ФИО педагога в teacher_directory_entry автоматически отражается в manual/classroom.
-- 3) В основных рабочих таблицах enforced FK по subject_id / teacher_id.

BEGIN;

-- -----------------------------
-- SUBJECT FK
-- -----------------------------

-- 1. Добавляем subject_id в curriculum_plan_entry, если его нет (в некоторых БД уже может быть)
ALTER TABLE curriculum_plan_entry
    ADD COLUMN IF NOT EXISTS subject_id BIGINT;

-- 2. Backfill subject_id по subjectName + curriculumPart -> SubjectType
WITH matched AS (
    SELECT c.id AS curriculum_id,
           s.id AS subject_id
    FROM curriculum_plan_entry c
    JOIN subject_catalog_entry s
      ON lower(trim(c."subjectName")) = lower(trim(s."subjectName"))
     AND (
            (c."curriculumPart" = 'CORE' AND s."subjectType" IN ('CORE','CORE_FORMABLE'))
         OR (c."curriculumPart" = 'FORMABLE' AND s."subjectType" IN ('FORMABLE','CORE_FORMABLE'))
         OR (c."curriculumPart" = 'EXTRACURRICULAR' AND s."subjectType" = 'EXTRACURRICULAR')
         OR (c."curriculumPart" = 'CORRECTIONAL' AND s."subjectType" IN ('FORMABLE','CORE_FORMABLE'))
         )
)
UPDATE curriculum_plan_entry c
SET subject_id = m.subject_id
FROM matched m
WHERE c.id = m.curriculum_id
  AND c.subject_id IS NULL;

-- 3. Если не нашли предмет — создаём в справочнике (чтобы миграция не падала)
INSERT INTO subject_catalog_entry("subjectName", "subjectType", "subjectAreaName", "subjectCoefficient", "createdAt")
SELECT DISTINCT
       trim(c."subjectName") AS subject_name,
       CASE
           WHEN c."curriculumPart" = 'EXTRACURRICULAR' THEN 'EXTRACURRICULAR'::subjecttype
           WHEN c."curriculumPart" = 'FORMABLE' THEN 'FORMABLE'::subjecttype
           WHEN c."curriculumPart" = 'CORRECTIONAL' THEN 'FORMABLE'::subjecttype
           ELSE 'CORE'::subjecttype
       END AS subject_type,
       'Без области',
       1,
       now()
FROM curriculum_plan_entry c
WHERE c.subject_id IS NULL
  AND coalesce(trim(c."subjectName"), '') <> '';

-- 4. Повторный backfill после auto-create
WITH matched AS (
    SELECT c.id AS curriculum_id,
           s.id AS subject_id
    FROM curriculum_plan_entry c
    JOIN subject_catalog_entry s
      ON lower(trim(c."subjectName")) = lower(trim(s."subjectName"))
     AND (
            (c."curriculumPart" = 'CORE' AND s."subjectType" IN ('CORE','CORE_FORMABLE'))
         OR (c."curriculumPart" = 'FORMABLE' AND s."subjectType" IN ('FORMABLE','CORE_FORMABLE'))
         OR (c."curriculumPart" = 'EXTRACURRICULAR' AND s."subjectType" = 'EXTRACURRICULAR')
         OR (c."curriculumPart" = 'CORRECTIONAL' AND s."subjectType" IN ('FORMABLE','CORE_FORMABLE'))
         )
)
UPDATE curriculum_plan_entry c
SET subject_id = m.subject_id
FROM matched m
WHERE c.id = m.curriculum_id
  AND c.subject_id IS NULL;

-- 5. manual_load_entry: backfill subject_id по subjectName
ALTER TABLE manual_load_entry
    ADD COLUMN IF NOT EXISTS subject_id BIGINT;

WITH ranked_subject AS (
    SELECT s.*,
           CASE
               WHEN s."subjectType" = 'CORE' THEN 1
               WHEN s."subjectType" = 'FORMABLE' THEN 2
               WHEN s."subjectType" = 'CORE_FORMABLE' THEN 3
               WHEN s."subjectType" = 'EXTRACURRICULAR' THEN 4
               ELSE 5
           END AS type_rank
    FROM subject_catalog_entry s
), chosen AS (
    SELECT m.id AS manual_id,
           rs.id AS subject_id,
           row_number() OVER (PARTITION BY m.id ORDER BY rs.type_rank, rs.id) AS rn
    FROM manual_load_entry m
    JOIN ranked_subject rs
      ON lower(trim(m."subjectName")) = lower(trim(rs."subjectName"))
)
UPDATE manual_load_entry m
SET subject_id = c.subject_id
FROM chosen c
WHERE m.id = c.manual_id
  AND c.rn = 1
  AND m.subject_id IS NULL;

-- Автосоздание отсутствующих предметов для manual
INSERT INTO subject_catalog_entry("subjectName", "subjectType", "subjectAreaName", "subjectCoefficient", "createdAt")
SELECT DISTINCT
       trim(m."subjectName"),
       'CORE'::subjecttype,
       'Без области',
       1,
       now()
FROM manual_load_entry m
WHERE m.subject_id IS NULL
  AND coalesce(trim(m."subjectName"), '') <> '';

WITH chosen AS (
    SELECT m.id AS manual_id,
           s.id AS subject_id,
           row_number() OVER (PARTITION BY m.id ORDER BY s.id) AS rn
    FROM manual_load_entry m
    JOIN subject_catalog_entry s
      ON lower(trim(m."subjectName")) = lower(trim(s."subjectName"))
)
UPDATE manual_load_entry m
SET subject_id = c.subject_id
FROM chosen c
WHERE m.id = c.manual_id
  AND c.rn = 1
  AND m.subject_id IS NULL;

-- FK + индексы
ALTER TABLE curriculum_plan_entry
    ADD CONSTRAINT fk_curriculum_subject FOREIGN KEY (subject_id) REFERENCES subject_catalog_entry(id) ON UPDATE RESTRICT ON DELETE RESTRICT;
CREATE INDEX IF NOT EXISTS idx_curriculum_subject_id ON curriculum_plan_entry(subject_id);

ALTER TABLE manual_load_entry
    ADD CONSTRAINT fk_manual_subject FOREIGN KEY (subject_id) REFERENCES subject_catalog_entry(id) ON UPDATE RESTRICT ON DELETE RESTRICT;
CREATE INDEX IF NOT EXISTS idx_manual_subject_id ON manual_load_entry(subject_id);

ALTER TABLE curriculum_plan_entry ALTER COLUMN subject_id SET NOT NULL;
ALTER TABLE manual_load_entry ALTER COLUMN subject_id SET NOT NULL;

-- 6. Синхронизация subject_id <-> subjectName при INSERT/UPDATE
CREATE OR REPLACE FUNCTION trg_sync_subject_fk() RETURNS trigger AS $$
DECLARE
    sid BIGINT;
    sname TEXT;
BEGIN
    IF NEW.subject_id IS NULL THEN
        IF coalesce(trim(NEW."subjectName"), '') = '' THEN
            RAISE EXCEPTION 'subjectName is required for %', TG_TABLE_NAME;
        END IF;
        SELECT id, "subjectName" INTO sid, sname
        FROM subject_catalog_entry
        WHERE lower(trim("subjectName")) = lower(trim(NEW."subjectName"))
        ORDER BY id
        LIMIT 1;
        IF sid IS NULL THEN
            INSERT INTO subject_catalog_entry("subjectName", "subjectType", "subjectAreaName", "subjectCoefficient", "createdAt")
            VALUES (trim(NEW."subjectName"), 'CORE', 'Без области', 1, now())
            RETURNING id, "subjectName" INTO sid, sname;
        END IF;
        NEW.subject_id := sid;
        NEW."subjectName" := sname;
    ELSE
        SELECT "subjectName" INTO sname FROM subject_catalog_entry WHERE id = NEW.subject_id;
        IF sname IS NULL THEN
            RAISE EXCEPTION 'subject_id=% not found for %', NEW.subject_id, TG_TABLE_NAME;
        END IF;
        NEW."subjectName" := sname;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_curriculum_sync_subject_fk ON curriculum_plan_entry;
CREATE TRIGGER trg_curriculum_sync_subject_fk
BEFORE INSERT OR UPDATE ON curriculum_plan_entry
FOR EACH ROW EXECUTE FUNCTION trg_sync_subject_fk();

DROP TRIGGER IF EXISTS trg_manual_sync_subject_fk ON manual_load_entry;
CREATE TRIGGER trg_manual_sync_subject_fk
BEFORE INSERT OR UPDATE ON manual_load_entry
FOR EACH ROW EXECUTE FUNCTION trg_sync_subject_fk();

-- 7. Автопротяжка rename subjectName -> dependent tables
CREATE OR REPLACE FUNCTION trg_propagate_subject_rename() RETURNS trigger AS $$
BEGIN
    IF coalesce(OLD."subjectName", '') <> coalesce(NEW."subjectName", '') THEN
        UPDATE curriculum_plan_entry SET "subjectName" = NEW."subjectName" WHERE subject_id = NEW.id;
        UPDATE manual_load_entry SET "subjectName" = NEW."subjectName" WHERE subject_id = NEW.id;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_subject_rename_propagation ON subject_catalog_entry;
CREATE TRIGGER trg_subject_rename_propagation
AFTER UPDATE OF "subjectName" ON subject_catalog_entry
FOR EACH ROW EXECUTE FUNCTION trg_propagate_subject_rename();

-- -----------------------------
-- TEACHER FK
-- -----------------------------

ALTER TABLE classroom_leadership_entry
    ADD COLUMN IF NOT EXISTS teacher_id BIGINT;

ALTER TABLE manual_load_entry
    ADD COLUMN IF NOT EXISTS teacher_id BIGINT;

-- Backfill teacher_id by fioTeacher
UPDATE classroom_leadership_entry c
SET teacher_id = t.id,
    "fioTeacher" = t."fioTeacher"
FROM teacher_directory_entry t
WHERE lower(trim(c."fioTeacher")) = lower(trim(t."fioTeacher"))
  AND c.teacher_id IS NULL;

UPDATE manual_load_entry m
SET teacher_id = t.id,
    "fioTeacher" = t."fioTeacher"
FROM teacher_directory_entry t
WHERE lower(trim(m."fioTeacher")) = lower(trim(t."fioTeacher"))
  AND m.teacher_id IS NULL;

-- Создаём отсутствующих педагогов по факту встречаемых ФИО
INSERT INTO teacher_directory_entry("fioTeacher", "createdAt")
SELECT DISTINCT trim(c."fioTeacher"), now()
FROM classroom_leadership_entry c
WHERE c.teacher_id IS NULL
  AND coalesce(trim(c."fioTeacher"), '') <> ''
ON CONFLICT ("fioTeacher") DO NOTHING;

INSERT INTO teacher_directory_entry("fioTeacher", "createdAt")
SELECT DISTINCT trim(m."fioTeacher"), now()
FROM manual_load_entry m
WHERE m.teacher_id IS NULL
  AND coalesce(trim(m."fioTeacher"), '') <> ''
ON CONFLICT ("fioTeacher") DO NOTHING;

-- Повторный backfill
UPDATE classroom_leadership_entry c
SET teacher_id = t.id,
    "fioTeacher" = t."fioTeacher"
FROM teacher_directory_entry t
WHERE lower(trim(c."fioTeacher")) = lower(trim(t."fioTeacher"))
  AND c.teacher_id IS NULL;

UPDATE manual_load_entry m
SET teacher_id = t.id,
    "fioTeacher" = t."fioTeacher"
FROM teacher_directory_entry t
WHERE lower(trim(m."fioTeacher")) = lower(trim(t."fioTeacher"))
  AND m.teacher_id IS NULL;

-- FK + индексы + not null
ALTER TABLE classroom_leadership_entry
    ADD CONSTRAINT fk_classroom_teacher FOREIGN KEY (teacher_id) REFERENCES teacher_directory_entry(id) ON UPDATE RESTRICT ON DELETE RESTRICT;
CREATE INDEX IF NOT EXISTS idx_classroom_teacher_id ON classroom_leadership_entry(teacher_id);
ALTER TABLE classroom_leadership_entry ALTER COLUMN teacher_id SET NOT NULL;

ALTER TABLE manual_load_entry
    ADD CONSTRAINT fk_manual_teacher FOREIGN KEY (teacher_id) REFERENCES teacher_directory_entry(id) ON UPDATE RESTRICT ON DELETE RESTRICT;
CREATE INDEX IF NOT EXISTS idx_manual_teacher_id ON manual_load_entry(teacher_id);
ALTER TABLE manual_load_entry ALTER COLUMN teacher_id SET NOT NULL;

-- Синхронизация teacher_id <-> fioTeacher
CREATE OR REPLACE FUNCTION trg_sync_teacher_fk() RETURNS trigger AS $$
DECLARE
    tid BIGINT;
    tfio TEXT;
BEGIN
    IF NEW.teacher_id IS NULL THEN
        IF coalesce(trim(NEW."fioTeacher"), '') = '' THEN
            RAISE EXCEPTION 'fioTeacher is required for %', TG_TABLE_NAME;
        END IF;
        SELECT id, "fioTeacher" INTO tid, tfio
        FROM teacher_directory_entry
        WHERE lower(trim("fioTeacher")) = lower(trim(NEW."fioTeacher"))
        ORDER BY id
        LIMIT 1;
        IF tid IS NULL THEN
            INSERT INTO teacher_directory_entry("fioTeacher", "createdAt")
            VALUES (trim(NEW."fioTeacher"), now())
            RETURNING id, "fioTeacher" INTO tid, tfio;
        END IF;
        NEW.teacher_id := tid;
        NEW."fioTeacher" := tfio;
    ELSE
        SELECT "fioTeacher" INTO tfio FROM teacher_directory_entry WHERE id = NEW.teacher_id;
        IF tfio IS NULL THEN
            RAISE EXCEPTION 'teacher_id=% not found for %', NEW.teacher_id, TG_TABLE_NAME;
        END IF;
        NEW."fioTeacher" := tfio;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_classroom_sync_teacher_fk ON classroom_leadership_entry;
CREATE TRIGGER trg_classroom_sync_teacher_fk
BEFORE INSERT OR UPDATE ON classroom_leadership_entry
FOR EACH ROW EXECUTE FUNCTION trg_sync_teacher_fk();

DROP TRIGGER IF EXISTS trg_manual_sync_teacher_fk ON manual_load_entry;
CREATE TRIGGER trg_manual_sync_teacher_fk
BEFORE INSERT OR UPDATE ON manual_load_entry
FOR EACH ROW EXECUTE FUNCTION trg_sync_teacher_fk();

-- Автопротяжка rename fioTeacher -> dependent tables
CREATE OR REPLACE FUNCTION trg_propagate_teacher_rename() RETURNS trigger AS $$
BEGIN
    IF coalesce(OLD."fioTeacher", '') <> coalesce(NEW."fioTeacher", '') THEN
        UPDATE classroom_leadership_entry SET "fioTeacher" = NEW."fioTeacher" WHERE teacher_id = NEW.id;
        UPDATE manual_load_entry SET "fioTeacher" = NEW."fioTeacher" WHERE teacher_id = NEW.id;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_teacher_rename_propagation ON teacher_directory_entry;
CREATE TRIGGER trg_teacher_rename_propagation
AFTER UPDATE OF "fioTeacher" ON teacher_directory_entry
FOR EACH ROW EXECUTE FUNCTION trg_propagate_teacher_rename();

COMMIT;
