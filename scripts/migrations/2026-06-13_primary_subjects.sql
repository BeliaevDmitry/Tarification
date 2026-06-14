CREATE TABLE IF NOT EXISTS primary_subject_rule (
    id BIGSERIAL PRIMARY KEY,
    "primarySubject" VARCHAR(255) NOT NULL,
    "ruleType" VARCHAR(32) NOT NULL DEFAULT 'KEYWORDS',
    "ruleValue" VARCHAR(2000) NOT NULL DEFAULT '',
    priority INTEGER NOT NULL DEFAULT 100,
    "createdAt" TIMESTAMP NOT NULL DEFAULT now(),
    "updatedAt" TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT uk_primary_subject_rule_name UNIQUE ("primarySubject")
);

CREATE TABLE IF NOT EXISTS teacher_primary_subject_assignment (
    id BIGSERIAL PRIMARY KEY,
    "academicYear" VARCHAR(32) NOT NULL,
    "teacherId" BIGINT NOT NULL,
    "primarySubject" VARCHAR(255) NOT NULL,
    mode VARCHAR(16) NOT NULL DEFAULT 'AUTO',
    "updatedAt" TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT uk_teacher_primary_subject_year UNIQUE ("academicYear", "teacherId"),
    CONSTRAINT fk_teacher_primary_subject_teacher
        FOREIGN KEY ("teacherId") REFERENCES teacher_directory_entry(id) ON DELETE CASCADE
);
