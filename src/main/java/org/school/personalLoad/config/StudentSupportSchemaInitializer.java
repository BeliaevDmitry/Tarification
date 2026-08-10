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
        log.info("Схема контингента и поддержки обучающихся проверена");
    }
}
