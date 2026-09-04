package com.localai.workspace.agent;

import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

@Service
public class DatabaseReadOnlyService {

    private static final String PGVECTOR_QUERY =
            "SELECT EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'vector')";

    private final DataSource dataSource;
    private final LocalEnvironmentProperties properties;

    public DatabaseReadOnlyService(DataSource dataSource, LocalEnvironmentProperties properties) {
        this.dataSource = dataSource;
        this.properties = properties;
    }

    public DatabaseStatusResult getStatus() {
        int timeoutSeconds = Math.max(1, (int) properties.httpTimeout().toSeconds());
        try (Connection connection = dataSource.getConnection()) {
            if (!connection.isValid(timeoutSeconds)) {
                return failure(LocalEnvironmentStatus.UNAVAILABLE, "Database connection is not valid");
            }
            try (PreparedStatement statement = connection.prepareStatement(PGVECTOR_QUERY)) {
                statement.setQueryTimeout(timeoutSeconds);
                try (ResultSet resultSet = statement.executeQuery()) {
                    boolean pgvector = resultSet.next() && resultSet.getBoolean(1);
                    return new DatabaseStatusResult(
                            LocalEnvironmentStatus.AVAILABLE, true, pgvector, null
                    );
                }
            }
        } catch (SQLException exception) {
            String sqlState = exception.getSQLState();
            if (sqlState != null && sqlState.startsWith("28")) {
                return failure(LocalEnvironmentStatus.ACCESS_DENIED, "Database access was denied");
            }
            if (sqlState != null && sqlState.startsWith("08")) {
                return failure(LocalEnvironmentStatus.NOT_RUNNING, "Database is not currently reachable");
            }
            return failure(LocalEnvironmentStatus.FAILED, "Database status could not be read");
        }
    }

    private DatabaseStatusResult failure(LocalEnvironmentStatus status, String reason) {
        return new DatabaseStatusResult(status, false, false, reason);
    }
}
