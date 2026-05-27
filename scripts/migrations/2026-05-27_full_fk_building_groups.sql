-- Полноценная FK-схема для корпусов:
-- 1) справочник групп корпусов (building_group)
-- 2) school_building -> building_group (много адресов на одну группу)
-- 3) таблицы с numberSchoolBuilding получают building_group_id + FK
-- 4) триггеры синхронизации старого текстового кода и нового FK

BEGIN;

-- 0. Базовая таблица групп корпусов
CREATE TABLE IF NOT EXISTS building_group (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(64) NOT NULL,
    name VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT uk_building_group_code UNIQUE (code)
);

-- 1. Наполняем справочник групп из существующих school_building
INSERT INTO building_group(code, name)
SELECT DISTINCT
       upper(replace(split_part(coalesce(sb.code, ''), '|', 1), ' ', '')) AS grp_code,
       coalesce(nullif(trim(sb.name), ''), upper(replace(split_part(coalesce(sb.code, ''), '|', 1), ' ', ''))) AS grp_name
FROM school_building sb
WHERE coalesce(trim(sb.code), '') <> '' OR coalesce(trim(sb.name), '') <> ''
ON CONFLICT (code) DO UPDATE
SET name = EXCLUDED.name;

-- 2. school_building: привязка площадки к группе
ALTER TABLE school_building
    ADD COLUMN IF NOT EXISTS building_group_id BIGINT;

UPDATE school_building sb
SET building_group_id = bg.id,
    code = bg.code
FROM building_group bg
WHERE bg.code = upper(replace(split_part(coalesce(sb.code, ''), '|', 1), ' ', ''));

ALTER TABLE school_building
    ALTER COLUMN building_group_id SET NOT NULL;

ALTER TABLE school_building
    ADD CONSTRAINT fk_school_building_group
    FOREIGN KEY (building_group_id) REFERENCES building_group(id) ON UPDATE RESTRICT ON DELETE RESTRICT;

CREATE INDEX IF NOT EXISTS idx_school_building_group_id ON school_building(building_group_id);

-- 3. Универсальная функция синхронизации <text code> <-> building_group_id
CREATE OR REPLACE FUNCTION trg_sync_building_group_fk() RETURNS trigger AS $$
DECLARE
    normalized_code TEXT;
    resolved_id BIGINT;
BEGIN
    normalized_code := upper(replace(split_part(coalesce(NEW."numberSchoolBuilding", ''), '|', 1), ' ', ''));

    IF NEW.building_group_id IS NULL THEN
        IF coalesce(normalized_code, '') = '' THEN
            RAISE EXCEPTION 'numberSchoolBuilding is required for table %', TG_TABLE_NAME;
        END IF;

        SELECT id INTO resolved_id FROM building_group WHERE code = normalized_code;
        IF resolved_id IS NULL THEN
            INSERT INTO building_group(code, name) VALUES (normalized_code, normalized_code)
            ON CONFLICT (code) DO NOTHING;
            SELECT id INTO resolved_id FROM building_group WHERE code = normalized_code;
        END IF;

        NEW.building_group_id := resolved_id;
    ELSE
        SELECT code INTO normalized_code FROM building_group WHERE id = NEW.building_group_id;
        IF normalized_code IS NULL THEN
            RAISE EXCEPTION 'building_group_id=% not found for table %', NEW.building_group_id, TG_TABLE_NAME;
        END IF;
    END IF;

    NEW."numberSchoolBuilding" := normalized_code;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- 4. classroom_leadership_entry
ALTER TABLE classroom_leadership_entry ADD COLUMN IF NOT EXISTS building_group_id BIGINT;
UPDATE classroom_leadership_entry t
SET building_group_id = bg.id,
    "numberSchoolBuilding" = bg.code
FROM building_group bg
WHERE bg.code = upper(replace(split_part(coalesce(t."numberSchoolBuilding", ''), '|', 1), ' ', ''));
ALTER TABLE classroom_leadership_entry ALTER COLUMN building_group_id SET NOT NULL;
ALTER TABLE classroom_leadership_entry
    ADD CONSTRAINT fk_classroom_leadership_group FOREIGN KEY (building_group_id) REFERENCES building_group(id) ON UPDATE RESTRICT ON DELETE RESTRICT;
CREATE INDEX IF NOT EXISTS idx_classroom_leadership_group_id ON classroom_leadership_entry(building_group_id);
DROP TRIGGER IF EXISTS trg_classroom_leadership_sync_building_group ON classroom_leadership_entry;
CREATE TRIGGER trg_classroom_leadership_sync_building_group
    BEFORE INSERT OR UPDATE ON classroom_leadership_entry
    FOR EACH ROW EXECUTE FUNCTION trg_sync_building_group_fk();

-- 5. curriculum_plan_entry
ALTER TABLE curriculum_plan_entry ADD COLUMN IF NOT EXISTS building_group_id BIGINT;
UPDATE curriculum_plan_entry t
SET building_group_id = bg.id,
    "numberSchoolBuilding" = bg.code
FROM building_group bg
WHERE bg.code = upper(replace(split_part(coalesce(t."numberSchoolBuilding", ''), '|', 1), ' ', ''));
ALTER TABLE curriculum_plan_entry ALTER COLUMN building_group_id SET NOT NULL;
ALTER TABLE curriculum_plan_entry
    ADD CONSTRAINT fk_curriculum_plan_group FOREIGN KEY (building_group_id) REFERENCES building_group(id) ON UPDATE RESTRICT ON DELETE RESTRICT;
CREATE INDEX IF NOT EXISTS idx_curriculum_plan_group_id ON curriculum_plan_entry(building_group_id);
DROP TRIGGER IF EXISTS trg_curriculum_plan_sync_building_group ON curriculum_plan_entry;
CREATE TRIGGER trg_curriculum_plan_sync_building_group
    BEFORE INSERT OR UPDATE ON curriculum_plan_entry
    FOR EACH ROW EXECUTE FUNCTION trg_sync_building_group_fk();

-- 6. manual_load_entry
ALTER TABLE manual_load_entry ADD COLUMN IF NOT EXISTS building_group_id BIGINT;
UPDATE manual_load_entry t
SET building_group_id = bg.id,
    "numberSchoolBuilding" = bg.code
FROM building_group bg
WHERE bg.code = upper(replace(split_part(coalesce(t."numberSchoolBuilding", ''), '|', 1), ' ', ''));
ALTER TABLE manual_load_entry ALTER COLUMN building_group_id SET NOT NULL;
ALTER TABLE manual_load_entry
    ADD CONSTRAINT fk_manual_load_group FOREIGN KEY (building_group_id) REFERENCES building_group(id) ON UPDATE RESTRICT ON DELETE RESTRICT;
CREATE INDEX IF NOT EXISTS idx_manual_load_group_id ON manual_load_entry(building_group_id);
DROP TRIGGER IF EXISTS trg_manual_load_sync_building_group ON manual_load_entry;
CREATE TRIGGER trg_manual_load_sync_building_group
    BEFORE INSERT OR UPDATE ON manual_load_entry
    FOR EACH ROW EXECUTE FUNCTION trg_sync_building_group_fk();

-- 7. meta_group
ALTER TABLE meta_group ADD COLUMN IF NOT EXISTS building_group_id BIGINT;
UPDATE meta_group t
SET building_group_id = bg.id,
    "numberSchoolBuilding" = bg.code
FROM building_group bg
WHERE bg.code = upper(replace(split_part(coalesce(t."numberSchoolBuilding", ''), '|', 1), ' ', ''));
ALTER TABLE meta_group ALTER COLUMN building_group_id SET NOT NULL;
ALTER TABLE meta_group
    ADD CONSTRAINT fk_meta_group_group FOREIGN KEY (building_group_id) REFERENCES building_group(id) ON UPDATE RESTRICT ON DELETE RESTRICT;
CREATE INDEX IF NOT EXISTS idx_meta_group_group_id ON meta_group(building_group_id);
DROP TRIGGER IF EXISTS trg_meta_group_sync_building_group ON meta_group;
CREATE TRIGGER trg_meta_group_sync_building_group
    BEFORE INSERT OR UPDATE ON meta_group
    FOR EACH ROW EXECUTE FUNCTION trg_sync_building_group_fk();

-- 8. teacher_directory_entry
ALTER TABLE teacher_directory_entry ADD COLUMN IF NOT EXISTS building_group_id BIGINT;
UPDATE teacher_directory_entry t
SET building_group_id = bg.id,
    "numberSchoolBuilding" = bg.code
FROM building_group bg
WHERE bg.code = upper(replace(split_part(coalesce(t."numberSchoolBuilding", ''), '|', 1), ' ', ''));
ALTER TABLE teacher_directory_entry ALTER COLUMN building_group_id SET NOT NULL;
ALTER TABLE teacher_directory_entry
    ADD CONSTRAINT fk_teacher_directory_group FOREIGN KEY (building_group_id) REFERENCES building_group(id) ON UPDATE RESTRICT ON DELETE RESTRICT;
CREATE INDEX IF NOT EXISTS idx_teacher_directory_group_id ON teacher_directory_entry(building_group_id);
DROP TRIGGER IF EXISTS trg_teacher_directory_sync_building_group ON teacher_directory_entry;
CREATE TRIGGER trg_teacher_directory_sync_building_group
    BEFORE INSERT OR UPDATE ON teacher_directory_entry
    FOR EACH ROW EXECUTE FUNCTION trg_sync_building_group_fk();

COMMIT;
