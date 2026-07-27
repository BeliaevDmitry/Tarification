package org.school.personalLoad.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.DependsOn;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;

@Slf4j
@Component
@DependsOn("entityManagerFactory")
@RequiredArgsConstructor
public class HrDocumentSchemaInitializer implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;
    private final DataSource dataSource;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        String database;
        try (Connection connection = dataSource.getConnection()) {
            database = connection.getMetaData().getDatabaseProductName();
        }
        if (!"PostgreSQL".equalsIgnoreCase(database)) return;

        jdbcTemplate.execute("ALTER TABLE additional_agreement ADD COLUMN IF NOT EXISTS teacher_id bigint");
        jdbcTemplate.execute("ALTER TABLE additional_agreement ADD COLUMN IF NOT EXISTS reissue_required boolean DEFAULT false");
        jdbcTemplate.execute("UPDATE additional_agreement SET reissue_required = false WHERE reissue_required IS NULL");
        jdbcTemplate.execute("ALTER TABLE additional_agreement ALTER COLUMN reissue_required SET DEFAULT false");
        jdbcTemplate.execute("ALTER TABLE additional_agreement ALTER COLUMN reissue_required SET NOT NULL");
        jdbcTemplate.execute("ALTER TABLE additional_agreement ALTER COLUMN contract_id DROP NOT NULL");
        jdbcTemplate.execute("""
                UPDATE additional_agreement agreement
                   SET teacher_id = contract.teacher_id
                  FROM employment_contract contract
                 WHERE agreement.contract_id = contract.id
                   AND agreement.teacher_id IS NULL
                """);
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_additional_agreement_teacher ON additional_agreement(teacher_id)");
        jdbcTemplate.execute("""
                DO $$
                BEGIN
                    IF NOT EXISTS (
                        SELECT 1 FROM pg_constraint WHERE conname = 'fk_additional_agreement_teacher'
                    ) THEN
                        ALTER TABLE additional_agreement
                            ADD CONSTRAINT fk_additional_agreement_teacher
                            FOREIGN KEY (teacher_id) REFERENCES teacher_directory_entry(id);
                    END IF;
                END $$
                """);
        log.info("Схема кадровых документов допускает черновики дополнительных соглашений до заполнения договора");
    }
}
