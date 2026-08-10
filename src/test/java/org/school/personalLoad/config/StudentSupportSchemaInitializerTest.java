package org.school.personalLoad.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StudentSupportSchemaInitializerTest {

    @Test
    void addsMissingIdentityColumnsToExistingPostgresContingentTable() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        DatabaseMetaData metaData = mock(DatabaseMetaData.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metaData);
        when(metaData.getDatabaseProductName()).thenReturn("PostgreSQL");

        new StudentSupportSchemaInitializer(jdbcTemplate, dataSource)
                .run(new DefaultApplicationArguments(new String[0]));

        verify(jdbcTemplate).execute("ALTER TABLE contingent_student ADD COLUMN IF NOT EXISTS student_id bigint");
        verify(jdbcTemplate).execute("ALTER TABLE contingent_student ADD COLUMN IF NOT EXISTS identity_match_status varchar(48) DEFAULT 'PENDING'");
        verify(jdbcTemplate).execute("UPDATE contingent_student SET identity_match_status = 'PENDING' WHERE identity_match_status IS NULL");
    }
}
