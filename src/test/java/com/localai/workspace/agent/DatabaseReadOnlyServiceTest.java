package com.localai.workspace.agent;

import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DatabaseReadOnlyServiceTest {

    private final LocalEnvironmentProperties properties = new LocalEnvironmentProperties(
            Duration.ofSeconds(5), Duration.ofSeconds(3), 65_536, 100
    );

    @Test
    void reportsApplicationDatabaseAndPgvectorAvailability() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.isValid(3)).thenReturn(true);
        when(connection.prepareStatement(
                "SELECT EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'vector')"
        )).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true);
        when(resultSet.getBoolean(1)).thenReturn(true);

        DatabaseStatusResult result = new DatabaseReadOnlyService(dataSource, properties).getStatus();

        assertThat(result.status()).isEqualTo(LocalEnvironmentStatus.AVAILABLE);
        assertThat(result.reachable()).isTrue();
        assertThat(result.pgvectorAvailable()).isTrue();
        assertThat(result.toString()).doesNotContain("password", "jdbc:");
        verify(statement).setQueryTimeout(3);
    }

    @Test
    void returnsSafeUnavailableStateWithoutSqlDetails() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        SQLException failure = new SQLException(
                "connection to jdbc:postgresql://secret-host failed for password=secret", "08001"
        );
        when(dataSource.getConnection()).thenThrow(failure);

        DatabaseStatusResult result = new DatabaseReadOnlyService(dataSource, properties).getStatus();

        assertThat(result.status()).isEqualTo(LocalEnvironmentStatus.NOT_RUNNING);
        assertThat(result.reachable()).isFalse();
        assertThat(result.reason()).doesNotContain("secret", "jdbc", "password");
    }
}
