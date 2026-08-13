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
        jdbcTemplate.execute("ALTER TABLE student_support_document ADD COLUMN IF NOT EXISTS education_program varchar(64)");
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
        jdbcTemplate.execute("ALTER TABLE student_support_document ALTER COLUMN received_at DROP NOT NULL");
        jdbcTemplate.execute("ALTER TABLE student_support_status ADD COLUMN IF NOT EXISTS source_document_id bigint");
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
    }
}
