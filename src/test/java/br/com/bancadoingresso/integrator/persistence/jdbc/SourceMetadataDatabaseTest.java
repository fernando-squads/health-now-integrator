package br.com.bancadoingresso.integrator.persistence.jdbc;

import br.com.bancadoingresso.integrator.extraction.*;
import org.junit.Test;
import org.junit.Assume;
import java.sql.*;
import java.util.*;
import static org.junit.Assert.*;

/** Explicit read-only schema verification; never exports or prints source records. */
public class SourceMetadataDatabaseTest {
    @Test public void sourceContractMatchesTheReadOnlyPecSchema() throws Exception {
        Assume.assumeTrue(Boolean.getBoolean("esus.schema"));
        Properties properties = new Properties();
        properties.setProperty("user", System.getProperty("esus.user", System.getProperty("user.name")));
        properties.setProperty("password", System.getenv("ESUS_PASSWORD") == null ? "" : System.getenv("ESUS_PASSWORD"));
        properties.setProperty("options", "-c default_transaction_read_only=on -c statement_timeout=5000");
        String url = "jdbc:postgresql://" + System.getProperty("esus.host", "127.0.0.1") + ":"
            + System.getProperty("esus.port", "5432") + "/" + System.getProperty("esus.database", "esus");
        try (Connection connection = DriverManager.getConnection(url, properties)) {
            connection.setReadOnly(true);
            connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            connection.setAutoCommit(false);
            try {
                Map<String, Object> source = new SourceIdentityJDBC(connection, 5).read();
                assertNotNull(source.get("installation_uuid"));
                assertFalse(((List<?>) source.get("municipalities")).isEmpty());
                for (ExtractionRepository repository : ExtractionService.repositories(connection, 5)) {
                    for (ExtractionQuery query : repository.queries()) {
                        try (PreparedStatement statement = connection.prepareStatement(SqlQueryLoader.load(query.resource))) {
                            statement.setDate(1, java.sql.Date.valueOf("2026-10-09"));
                            statement.setLong(2, Long.MIN_VALUE);
                            statement.setInt(3, 0);
                            statement.setQueryTimeout(5);
                            try (ResultSet result = statement.executeQuery()) {
                                assertFalse(result.next());
                                assertTrue(result.getMetaData().getColumnCount() > 2);
                            }
                        }
                    }
                }
            } finally { connection.rollback(); }
        } catch (SQLException error) {
            throw new SQLException("PEC schema verification failed.", error.getSQLState());
        }
    }
}
