package org.school.personalLoad.pa.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class PaSchemaCompatInitializer {

    private final JdbcTemplate jdbcTemplate;

    @Bean
    public ApplicationRunner ensurePaSchemaCompatibility() {
        return args -> {
            try {
                jdbcTemplate.execute("ALTER TABLE pa_specification ADD COLUMN IF NOT EXISTS grading_scale VARCHAR(20) NOT NULL DEFAULT 'FIVE_POINT'");
                jdbcTemplate.execute("ALTER TABLE pa_specification ADD COLUMN IF NOT EXISTS pass_percent INTEGER");
                jdbcTemplate.execute("UPDATE pa_specification SET grading_scale='FIVE_POINT' WHERE grading_scale IS NULL");
            } catch (Exception e) {
                log.warn("PA schema compatibility check failed: {}", e.getMessage());
            }
        };
    }
}
