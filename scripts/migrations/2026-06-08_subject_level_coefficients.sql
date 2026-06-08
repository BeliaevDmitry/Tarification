CREATE TABLE IF NOT EXISTS subject_level_coefficient_entry (
    id BIGSERIAL PRIMARY KEY,
    "subjectName" VARCHAR(255) NOT NULL,
    "educationStage" VARCHAR(32) NOT NULL,
    coefficient NUMERIC(19, 2) NOT NULL DEFAULT 1,
    "createdAt" TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT uk_subject_level_coefficient_name_stage UNIQUE ("subjectName", "educationStage")
);
