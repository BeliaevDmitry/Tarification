package org.school.personalLoad.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.DependsOn;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;

/**
 * Keeps installations created before the student support module compatible
 * with the current contingent model. Hibernate cannot safely add a mandatory
 * column to an already populated table on every PostgreSQL version, so this
 * small idempotent migration is intentionally performed at application start.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@DependsOn("entityManagerFactory")
@RequiredArgsConstructor
public class StudentSupportSchemaInitializer implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;
    private final DataSource dataSource;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        String database;
        try (Connection connection = dataSource.getConnection()) {
            database = connection.getMetaData().getDatabaseProductName();
        }
        if (!"PostgreSQL".equalsIgnoreCase(database)) {
            return;
        }

        jdbcTemplate.execute("ALTER TABLE contingent_student ADD COLUMN IF NOT EXISTS student_id bigint");
        jdbcTemplate.execute("ALTER TABLE contingent_student ADD COLUMN IF NOT EXISTS identity_match_status varchar(48) DEFAULT 'PENDING'");
        jdbcTemplate.execute("UPDATE contingent_student SET identity_match_status = 'PENDING' WHERE identity_match_status IS NULL");
        jdbcTemplate.execute("ALTER TABLE contingent_student ALTER COLUMN identity_match_status SET DEFAULT 'PENDING'");
        jdbcTemplate.execute("ALTER TABLE contingent_student ALTER COLUMN identity_match_status SET NOT NULL");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_contingent_student_profile ON contingent_student(student_id)");
        migrateCertificates();
        log.info("Схема контингента и поддержки обучающихся проверена");
    }

    private void migrateCertificates() {
        jdbcTemplate.execute("ALTER TABLE student_support_document ADD COLUMN IF NOT EXISTS nosology_code varchar(16)");
        jdbcTemplate.execute("ALTER TABLE student_support_document ADD COLUMN IF NOT EXISTS education_stage varchar(16)");
        jdbcTemplate.execute("ALTER TABLE student_support_document ADD COLUMN IF NOT EXISTS education_program varchar(2000)");
        jdbcTemplate.execute("ALTER TABLE student_support_document ALTER COLUMN education_program TYPE varchar(2000) USING education_program::text");
        jdbcTemplate.execute("""
                UPDATE student_support_document
                SET education_program = CASE education_program
                    WHEN 'DEAF' THEN 'Глухие'
                    WHEN 'HARD_OF_HEARING' THEN 'Слабослышащие, позднооглохшие, кохлеарно имплантированные, глухие'
                    WHEN 'BLIND' THEN 'Слепые'
                    WHEN 'VISUALLY_IMPAIRED' THEN 'Слабовидящие'
                    WHEN 'TNR' THEN 'ТНР'
                    WHEN 'NODA' THEN 'НОДА'
                    WHEN 'ZPR' THEN 'ЗПР'
                    WHEN 'RAS' THEN 'РАС'
                    WHEN 'UO' THEN 'УО'
                    ELSE education_program
                END
                WHERE education_program IN ('DEAF', 'HARD_OF_HEARING', 'BLIND', 'VISUALLY_IMPAIRED',
                                            'TNR', 'NODA', 'ZPR', 'RAS', 'UO')
                """);
        jdbcTemplate.execute("ALTER TABLE student_support_document ADD COLUMN IF NOT EXISTS prolongation_available boolean DEFAULT false");
        jdbcTemplate.execute("UPDATE student_support_document SET prolongation_available = false WHERE prolongation_available IS NULL");
        jdbcTemplate.execute("ALTER TABLE student_support_document ALTER COLUMN prolongation_available SET NOT NULL");
        jdbcTemplate.execute("ALTER TABLE student_support_document ADD COLUMN IF NOT EXISTS prolongation_used boolean DEFAULT false");
        jdbcTemplate.execute("UPDATE student_support_document SET prolongation_used = false WHERE prolongation_used IS NULL");
        jdbcTemplate.execute("ALTER TABLE student_support_document ALTER COLUMN prolongation_used SET NOT NULL");
        jdbcTemplate.execute("ALTER TABLE student_support_document ADD COLUMN IF NOT EXISTS prolonged_grade integer");
        jdbcTemplate.execute("ALTER TABLE student_support_document ADD COLUMN IF NOT EXISTS prolonged_academic_year varchar(16)");
        jdbcTemplate.execute("ALTER TABLE student_support_document ADD COLUMN IF NOT EXISTS ipra_present boolean DEFAULT false");
        jdbcTemplate.execute("UPDATE student_support_document SET ipra_present = false WHERE ipra_present IS NULL");
        jdbcTemplate.execute("ALTER TABLE student_support_document ALTER COLUMN ipra_present SET NOT NULL");
        jdbcTemplate.execute("UPDATE student_support_document SET accepted_form = 'COPY' WHERE document_type = 'MSE_CERTIFICATE' AND accepted_form <> 'COPY'");
        jdbcTemplate.execute("ALTER TABLE student_support_document ALTER COLUMN received_at DROP NOT NULL");
        jdbcTemplate.execute("ALTER TABLE student_support_status ADD COLUMN IF NOT EXISTS source_document_id bigint");
        jdbcTemplate.execute("""
                UPDATE student_support_status status
                SET category = 'K2',
                    nosology_id = NULL,
                    nosology_code_snapshot = NULL,
                    updated_at = now()
                WHERE status.source_document_id IN (
                    SELECT document.id
                    FROM student_support_document document
                    WHERE document.document_type = 'MSE_CERTIFICATE'
                )
                """);
        jdbcTemplate.execute("CREATE UNIQUE INDEX IF NOT EXISTS uk_support_status_source_document ON student_support_status(source_document_id) WHERE source_document_id IS NOT NULL");
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS correction_specialist_catalog_entry (
                    id bigserial PRIMARY KEY,
                    name varchar(255) NOT NULL,
                    active boolean NOT NULL DEFAULT true,
                    built_in boolean NOT NULL DEFAULT false,
                    created_at timestamp NOT NULL DEFAULT now(),
                    updated_at timestamp NOT NULL DEFAULT now(),
                    CONSTRAINT uk_correction_specialist_name UNIQUE (name)
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS student_support_document_correction (
                    id bigserial PRIMARY KEY,
                    document_id bigint NOT NULL REFERENCES student_support_document(id) ON DELETE CASCADE,
                    specialist_id bigint NOT NULL REFERENCES correction_specialist_catalog_entry(id),
                    tasks varchar(4000) NOT NULL,
                    created_at timestamp NOT NULL DEFAULT now(),
                    updated_at timestamp NOT NULL DEFAULT now(),
                    CONSTRAINT uk_support_document_correction UNIQUE (document_id, specialist_id)
                )
                """);
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_support_document_correction_document ON student_support_document_correction(document_id)");
        migrateSpecialistWorkspace();
        normalizeNinthGradeConclusionDeadlines();
    }

    private void migrateSpecialistWorkspace() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS ovz_specialist_support_entry (
                    id bigserial PRIMARY KEY,
                    academic_year varchar(16) NOT NULL,
                    student_id bigint NOT NULL REFERENCES student_profile(id),
                    specialist_id bigint NOT NULL REFERENCES correction_specialist_catalog_entry(id),
                    child_deficits text,
                    child_resources text,
                    annual_tasks text,
                    planned_results text,
                    updated_by_user_id bigint,
                    updated_by_name varchar(255),
                    created_at timestamp NOT NULL DEFAULT now(),
                    updated_at timestamp NOT NULL DEFAULT now(),
                    CONSTRAINT uk_ovz_support_entry UNIQUE (academic_year, student_id, specialist_id)
                )
                """);
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_ovz_support_entry_student_year ON ovz_specialist_support_entry(student_id, academic_year)");
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS ovz_specialist_workspace_settings (
                    id bigserial PRIMARY KEY,
                    responsible_teacher_id bigint REFERENCES teacher_directory_entry(id),
                    updated_at timestamp NOT NULL DEFAULT now()
                )
                """);
    }

    private void normalizeNinthGradeConclusionDeadlines() {
        jdbcTemplate.execute("""
                UPDATE student_support_document document
                SET valid_to = make_date(
                        split_part(document.academic_year, '/', 2)::integer
                        + 9 - substring(enrollment.class_name from '^[[:space:]]*([0-9]{1,2})')::integer
                        + CASE WHEN document.prolongation_available AND NOT document.prolongation_used THEN 1 ELSE 0 END,
                        8, 31),
                    updated_at = now()
                FROM student_class_enrollment enrollment
                WHERE document.document_type = 'CPMPC_CONCLUSION'
                  AND document.nosology_code ~ '^[ИО]9[.][0-9]$'
                  AND document.academic_year ~ '^[0-9]{4}/[0-9]{4}$'
                  AND enrollment.student_id = document.student_id
                  AND enrollment.academic_year = document.academic_year
                  AND substring(enrollment.class_name from '^[[:space:]]*([0-9]{1,2})')::integer BETWEEN 1 AND 11
                  AND enrollment.id = (
                      SELECT candidate.id
                      FROM student_class_enrollment candidate
                      WHERE candidate.student_id = document.student_id
                        AND candidate.academic_year = document.academic_year
                        AND substring(candidate.class_name from '^[[:space:]]*([0-9]{1,2})') IS NOT NULL
                      ORDER BY (candidate.valid_to IS NULL) DESC,
                               candidate.valid_from DESC NULLS LAST,
                               candidate.id DESC
                      LIMIT 1
                  )
                """);
        jdbcTemplate.execute("""
                UPDATE student_support_document document
                SET valid_to = make_date(
                        extract(year from student.birth_date)::integer + 15
                        + CASE WHEN document.prolongation_available AND NOT document.prolongation_used THEN 1 ELSE 0 END,
                        8, 31),
                    updated_at = now()
                FROM student_profile student
                WHERE document.document_type = 'CPMPC_CONCLUSION'
                  AND document.nosology_code ~ '^[ИО]9[.][0-9]$'
                  AND student.id = document.student_id
                  AND student.birth_date IS NOT NULL
                  AND NOT EXISTS (
                      SELECT 1
                      FROM student_class_enrollment enrollment
                      WHERE enrollment.student_id = document.student_id
                        AND enrollment.academic_year = document.academic_year
                        AND substring(enrollment.class_name from '^[[:space:]]*([0-9]{1,2})') IS NOT NULL
                  )
                """);
        jdbcTemplate.execute("""
                UPDATE student_support_status status
                SET valid_to = document.valid_to,
                    updated_at = now()
                FROM student_support_document document
                WHERE status.source_document_id = document.id
                  AND document.document_type = 'CPMPC_CONCLUSION'
                  AND document.nosology_code ~ '^[ИО]9[.][0-9]$'
                """);
    }
}
