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
public class CurriculumModuleSchemaInitializer implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;
    private final DataSource dataSource;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        String database;
        try (Connection connection = dataSource.getConnection()) {
            database = connection.getMetaData().getDatabaseProductName();
        }
        if (!"PostgreSQL".equalsIgnoreCase(database)) return;

        jdbcTemplate.execute("ALTER TABLE curriculum_plan_entry ADD COLUMN IF NOT EXISTS modular_system boolean NOT NULL DEFAULT false");
        jdbcTemplate.execute("ALTER TABLE curriculum_plan_entry ADD COLUMN IF NOT EXISTS subject_requirement varchar(32)");
        jdbcTemplate.execute("UPDATE curriculum_plan_entry SET subject_requirement = CASE WHEN curriculum_part = 'CORE' THEN 'MANDATORY' ELSE 'SCHOOL_CHOICE' END WHERE subject_requirement IS NULL");
        jdbcTemplate.execute("ALTER TABLE curriculum_plan_entry ALTER COLUMN subject_requirement SET NOT NULL");
        jdbcTemplate.execute("ALTER TABLE curriculum_plan_entry ALTER COLUMN subject_requirement SET DEFAULT 'MANDATORY'");
        jdbcTemplate.execute("ALTER TABLE curriculum_plan_entry ADD COLUMN IF NOT EXISTS subgroup_policy varchar(32)");
        jdbcTemplate.execute("UPDATE curriculum_plan_entry SET subgroup_policy = 'RECOMMENDED' WHERE subgroup_policy IS NULL");
        jdbcTemplate.execute("ALTER TABLE curriculum_plan_entry ALTER COLUMN subgroup_policy SET NOT NULL");
        jdbcTemplate.execute("ALTER TABLE curriculum_plan_entry ALTER COLUMN subgroup_policy SET DEFAULT 'RECOMMENDED'");
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS curriculum_module (
                    id bigserial PRIMARY KEY,
                    curriculum_entry_id bigint NOT NULL REFERENCES curriculum_plan_entry(id) ON DELETE CASCADE,
                    module_order integer NOT NULL,
                    module_name varchar(255) NOT NULL,
                    planned_hours numeric(10, 2) NOT NULL,
                    subgroup_required boolean NOT NULL DEFAULT false,
                    subgroup_count integer NOT NULL DEFAULT 0,
                    education_level varchar(32) NOT NULL,
                    subgroup1_hours integer,
                    subgroup1_education_level varchar(32),
                    subgroup2_hours integer,
                    subgroup2_education_level varchar(32),
                    CONSTRAINT uk_curriculum_module_order UNIQUE (curriculum_entry_id, module_order)
                )
                """);
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_curriculum_module_entry ON curriculum_module(curriculum_entry_id)");
        jdbcTemplate.execute("ALTER TABLE manual_load_entry ADD COLUMN IF NOT EXISTS curriculum_module_id bigint");
        jdbcTemplate.execute("""
                DO $$
                BEGIN
                    IF NOT EXISTS (
                        SELECT 1 FROM pg_constraint WHERE conname = 'fk_manual_load_curriculum_module'
                    ) THEN
                        ALTER TABLE manual_load_entry
                            ADD CONSTRAINT fk_manual_load_curriculum_module
                            FOREIGN KEY (curriculum_module_id) REFERENCES curriculum_module(id) ON DELETE SET NULL;
                    END IF;
                END $$
                """);
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_manual_load_curriculum_module ON manual_load_entry(curriculum_module_id)");
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS contingent_class_size_override (
                    id bigserial PRIMARY KEY,
                    academic_year varchar(255) NOT NULL,
                    class_name varchar(255) NOT NULL,
                    manual_students integer,
                    updated_at timestamp NOT NULL DEFAULT now(),
                    CONSTRAINT uk_contingent_class_size_override UNIQUE (academic_year, class_name)
                )
                """);
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_contingent_class_size_override_year ON contingent_class_size_override(academic_year)");
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS contingent_class_size_source_setting (
                    id bigserial PRIMARY KEY,
                    academic_year varchar(255) NOT NULL,
                    source varchar(32) NOT NULL DEFAULT 'AIS',
                    updated_at timestamp NOT NULL DEFAULT now(),
                    CONSTRAINT uk_contingent_class_size_source_year UNIQUE (academic_year)
                )
                """);
        log.info("Схема модульных предметов проверена");
    }
}
