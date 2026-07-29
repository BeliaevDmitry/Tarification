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

@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@DependsOn("entityManagerFactory")
@RequiredArgsConstructor
public class LoadInRateSchemaInitializer implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;
    private final DataSource dataSource;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        String database;
        try (Connection connection = dataSource.getConnection()) {
            database = connection.getMetaData().getDatabaseProductName();
        }
        if (!"PostgreSQL".equalsIgnoreCase(database)) return;

        migrateManualLoad();
        migrateEmploymentContracts();
        migrateRules();
        migrateMckoGradeBand();
        log.info("Схема часов внутри ставки и диапазонов МЦКО проверена");
    }

    private void migrateManualLoad() {
        jdbcTemplate.execute("ALTER TABLE manual_load_entry ADD COLUMN IF NOT EXISTS employment_contract_id bigint");
        jdbcTemplate.execute("ALTER TABLE manual_load_entry ADD COLUMN IF NOT EXISTS included_in_rate_hours numeric(10, 2)");
        jdbcTemplate.execute("ALTER TABLE manual_load_entry ADD COLUMN IF NOT EXISTS in_rate_allocation_confirmed boolean DEFAULT false");
        jdbcTemplate.execute("UPDATE manual_load_entry SET in_rate_allocation_confirmed = false WHERE in_rate_allocation_confirmed IS NULL");
        jdbcTemplate.execute("ALTER TABLE manual_load_entry ALTER COLUMN in_rate_allocation_confirmed SET DEFAULT false");
        jdbcTemplate.execute("ALTER TABLE manual_load_entry ALTER COLUMN in_rate_allocation_confirmed SET NOT NULL");
        jdbcTemplate.execute("ALTER TABLE manual_load_entry ADD COLUMN IF NOT EXISTS in_rate_reason varchar(1000)");
        jdbcTemplate.execute("ALTER TABLE manual_load_entry ADD COLUMN IF NOT EXISTS in_rate_updated_at timestamp");
        jdbcTemplate.execute("ALTER TABLE manual_load_entry ADD COLUMN IF NOT EXISTS in_rate_updated_by varchar(255)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_manual_load_employment_contract ON manual_load_entry(employment_contract_id)");
    }

    private void migrateEmploymentContracts() {
        jdbcTemplate.execute("ALTER TABLE employment_contract ADD COLUMN IF NOT EXISTS load_hours_may_be_included_in_rate boolean DEFAULT false");
        jdbcTemplate.execute("UPDATE employment_contract SET load_hours_may_be_included_in_rate = false WHERE load_hours_may_be_included_in_rate IS NULL");
        jdbcTemplate.execute("ALTER TABLE employment_contract ALTER COLUMN load_hours_may_be_included_in_rate SET DEFAULT false");
        jdbcTemplate.execute("ALTER TABLE employment_contract ALTER COLUMN load_hours_may_be_included_in_rate SET NOT NULL");
        jdbcTemplate.execute("ALTER TABLE employment_contract ADD COLUMN IF NOT EXISTS load_in_rate_rule_id bigint");
        jdbcTemplate.execute("ALTER TABLE employment_contract ADD COLUMN IF NOT EXISTS load_in_rate_document_label varchar(1000)");
    }

    private void migrateRules() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS load_in_rate_rule (
                    id bigserial PRIMARY KEY,
                    name varchar(255) NOT NULL,
                    document_label varchar(1000) NOT NULL,
                    active boolean NOT NULL DEFAULT true,
                    created_at timestamp NOT NULL DEFAULT now(),
                    updated_at timestamp NOT NULL DEFAULT now(),
                    CONSTRAINT uk_load_in_rate_rule_name UNIQUE (name)
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS load_in_rate_rule_band (
                    id bigserial PRIMARY KEY,
                    rule_id bigint NOT NULL,
                    min_total_hours numeric(10, 2) NOT NULL DEFAULT 0,
                    max_total_hours numeric(10, 2),
                    suggested_included_hours numeric(10, 2) NOT NULL DEFAULT 0,
                    rate_fraction numeric(5, 2)
                )
                """);
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_load_in_rate_rule_band_rule ON load_in_rate_rule_band(rule_id)");
    }

    private void migrateMckoGradeBand() {
        jdbcTemplate.execute("ALTER TABLE mcko_subject_mapping ADD COLUMN IF NOT EXISTS grade_band varchar(32) DEFAULT 'ALL'");
        jdbcTemplate.execute("UPDATE mcko_subject_mapping SET grade_band = 'ALL' WHERE grade_band IS NULL OR btrim(grade_band) = ''");
        jdbcTemplate.execute("ALTER TABLE mcko_subject_mapping ALTER COLUMN grade_band SET DEFAULT 'ALL'");
        jdbcTemplate.execute("ALTER TABLE mcko_subject_mapping ALTER COLUMN grade_band SET NOT NULL");
        jdbcTemplate.execute("ALTER TABLE mcko_subject_mapping DROP CONSTRAINT IF EXISTS uk_mcko_subject_mapping");
        jdbcTemplate.execute("ALTER TABLE mcko_subject_mapping ADD CONSTRAINT uk_mcko_subject_mapping UNIQUE (mcko_subject, subject_id, grade_band)");
    }
}
