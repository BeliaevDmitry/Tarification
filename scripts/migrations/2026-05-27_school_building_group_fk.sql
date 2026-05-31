-- 1) Убираем уникальность code, чтобы у одного корпуса (СП1) могло быть несколько адресов.
ALTER TABLE school_building DROP CONSTRAINT IF EXISTS uk_school_building_code;

-- 2) Нормализуем code у существующих записей: code := верхний регистр до "|".
UPDATE school_building
SET code = upper(replace(split_part(coalesce(code, ''), '|', 1), ' ', ''))
WHERE code IS NOT NULL;

-- 3) Для пустых code используем имя корпуса.
UPDATE school_building
SET code = upper(replace(coalesce(name, ''), ' ', ''))
WHERE coalesce(code, '') = '';
