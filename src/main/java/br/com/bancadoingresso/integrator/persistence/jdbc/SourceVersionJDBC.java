package br.com.bancadoingresso.integrator.persistence.jdbc;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/** Reads the PEC database version, not the PostgreSQL server or application binary version. */
public final class SourceVersionJDBC {
    private final Connection connection;
    private final int timeoutSeconds;

    public SourceVersionJDBC(Connection connection, int timeoutSeconds) {
        this.connection = connection;
        this.timeoutSeconds = timeoutSeconds;
    }

    public String getVersion() throws SQLException {
        String sql = SqlQueryLoader.load("/queries/configuration/find-source-version.sql");
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setQueryTimeout(timeoutSeconds);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) throw new SQLException("Missing version.");
                String version = result.getString("source_version");
                if (version == null || version.trim().isEmpty() || result.next()) {
                    throw new SQLException("Invalid version configuration.");
                }
                return version.trim();
            }
        } catch (SQLException error) {
            throw new SQLException("Unable to determine the PEC database version. Verify VERSAOBANCODADOS in tb_config_sistema.",
                error.getSQLState());
        }
    }
}
