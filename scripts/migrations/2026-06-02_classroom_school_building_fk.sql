-- Adds an independent FK from a class to the physical school building/site.
-- building_group_id remains the organizational СП ownership of the class.
-- school_building_id is resolved only by physical campus_address and is not constrained
-- to the same building_group_id as the class.

BEGIN;

ALTER TABLE classroom_leadership_entry
    ADD COLUMN IF NOT EXISTS school_building_id BIGINT;

DO $$
DECLARE
    missing_count BIGINT;
    duplicate_count BIGINT;
    sample_addresses TEXT;
BEGIN
    WITH class_address AS (
        SELECT c.id,
               c.campus_address,
               lower(regexp_replace(trim(coalesce(c.campus_address, '')), '\s+', ' ', 'g')) AS normalized_address
        FROM classroom_leadership_entry c
    ), address_match AS (
        SELECT ca.id,
               ca.campus_address,
               count(sb.id) AS match_count
        FROM class_address ca
        LEFT JOIN school_building sb
          ON lower(regexp_replace(trim(coalesce(sb.address, '')), '\s+', ' ', 'g')) = ca.normalized_address
        GROUP BY ca.id, ca.campus_address
    )
    SELECT count(*), string_agg(format('id=%s address="%s"', id, campus_address), '; ' ORDER BY id)
      INTO missing_count, sample_addresses
    FROM address_match
    WHERE match_count = 0;

    IF missing_count > 0 THEN
        RAISE EXCEPTION 'Cannot backfill classroom_leadership_entry.school_building_id: % class(es) have campus_address without a matching school_building.address. Examples: %',
            missing_count, left(coalesce(sample_addresses, ''), 1000);
    END IF;

    WITH class_address AS (
        SELECT c.id,
               c.campus_address,
               lower(regexp_replace(trim(coalesce(c.campus_address, '')), '\s+', ' ', 'g')) AS normalized_address
        FROM classroom_leadership_entry c
    ), address_match AS (
        SELECT ca.id,
               ca.campus_address,
               count(sb.id) AS match_count
        FROM class_address ca
        JOIN school_building sb
          ON lower(regexp_replace(trim(coalesce(sb.address, '')), '\s+', ' ', 'g')) = ca.normalized_address
        GROUP BY ca.id, ca.campus_address
    )
    SELECT count(*), string_agg(format('id=%s address="%s" matches=%s', id, campus_address, match_count), '; ' ORDER BY id)
      INTO duplicate_count, sample_addresses
    FROM address_match
    WHERE match_count > 1;

    IF duplicate_count > 0 THEN
        RAISE EXCEPTION 'Cannot backfill classroom_leadership_entry.school_building_id: % class(es) have campus_address matching multiple school_building.address rows. Examples: %',
            duplicate_count, left(coalesce(sample_addresses, ''), 1000);
    END IF;
END $$;

WITH unique_match AS (
    SELECT c.id AS classroom_id,
           min(sb.id) AS school_building_id
    FROM classroom_leadership_entry c
    JOIN school_building sb
      ON lower(regexp_replace(trim(coalesce(sb.address, '')), '\s+', ' ', 'g')) =
         lower(regexp_replace(trim(coalesce(c.campus_address, '')), '\s+', ' ', 'g'))
    GROUP BY c.id
)
UPDATE classroom_leadership_entry c
SET school_building_id = unique_match.school_building_id
FROM unique_match
WHERE c.id = unique_match.classroom_id
  AND c.school_building_id IS DISTINCT FROM unique_match.school_building_id;

DO $$
DECLARE
    null_count BIGINT;
BEGIN
    SELECT count(*) INTO null_count
    FROM classroom_leadership_entry
    WHERE school_building_id IS NULL;

    IF null_count > 0 THEN
        RAISE EXCEPTION 'Cannot set classroom_leadership_entry.school_building_id NOT NULL: % class(es) remain without a physical school building', null_count;
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_classroom_school_building') THEN
        ALTER TABLE classroom_leadership_entry
            ADD CONSTRAINT fk_classroom_school_building
            FOREIGN KEY (school_building_id) REFERENCES school_building(id)
            ON UPDATE RESTRICT ON DELETE RESTRICT;
    END IF;
END $$;

ALTER TABLE classroom_leadership_entry
    ALTER COLUMN school_building_id SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_classroom_school_building_id
    ON classroom_leadership_entry(school_building_id);

COMMIT;
