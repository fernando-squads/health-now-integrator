package br.com.bancadoingresso.integrator.persistence.jdbc;

import br.com.bancadoingresso.integrator.extraction.ExtractionRecord;
import java.sql.*;
import java.time.LocalDate;
import java.util.*;

/** Uses an externally owned, read-only snapshot connection; never commits or closes it. */
public abstract class ExtractionRepository {
    protected final Connection connection;
    private final int timeout;

    protected ExtractionRepository(Connection connection, int timeout) {
        this.connection = connection;
        this.timeout = timeout;
    }

    public abstract List<ExtractionQuery> queries();

    public long[] count(ExtractionQuery query, LocalDate cutoff) throws SQLException {
        try (PreparedStatement statement = prepare(query.resource.replace("find-page", "count"), cutoff);
             ResultSet result = statement.executeQuery()) {
            result.next();
            return new long[] {result.getLong("source_count"), result.getLong("skipped_count")};
        } catch (SQLException e) { throw safeFailure(query.entity, e); }
    }

    public List<ExtractionRecord> page(ExtractionQuery query, LocalDate cutoff, long afterId, int limit)
            throws SQLException {
        if (limit < 1 || limit > 10000) throw new IllegalArgumentException("Invalid page size.");
        try (PreparedStatement statement = prepare(query.resource, cutoff)) {
            statement.setLong(2, afterId);
            statement.setInt(3, limit);
            statement.setFetchSize(limit);
            try (ResultSet result = statement.executeQuery()) {
                List<ExtractionRecord> records = new ArrayList<ExtractionRecord>();
                while (result.next()) records.add(read(result));
                return records;
            }
        } catch (SQLException e) { throw safeFailure(query.entity, e); }
    }

    protected PreparedStatement prepare(String path, LocalDate cutoff) throws SQLException {
        if (!connection.isReadOnly() || connection.getAutoCommit()
                || connection.getTransactionIsolation() != Connection.TRANSACTION_REPEATABLE_READ) {
            throw new SQLException("Extraction requires a read-only repeatable-read transaction.");
        }
        PreparedStatement statement = connection.prepareStatement(SqlQueryLoader.load(path));
        try {
            statement.setQueryTimeout(timeout);
            statement.setDate(1, java.sql.Date.valueOf(cutoff));
            return statement;
        } catch (SQLException e) { statement.close(); throw e; }
    }

    protected ExtractionRecord read(ResultSet result) throws SQLException {
        Map<String, Object> fields = new LinkedHashMap<String, Object>();
        ResultSetMetaData metadata = result.getMetaData();
        for (int i = 1; i <= metadata.getColumnCount(); i++) {
            String name = metadata.getColumnLabel(i);
            if (name.startsWith("_")) continue;
            Object value;
            if (metadata.getColumnType(i) == Types.TIMESTAMP_WITH_TIMEZONE
                    || "timestamptz".equals(metadata.getColumnTypeName(i))) {
                java.time.OffsetDateTime timestamp = result.getObject(i, java.time.OffsetDateTime.class);
                value = timestamp == null ? null : timestamp.toInstant().toString();
            } else if (metadata.getColumnType(i) == Types.DATE) {
                java.sql.Date date = result.getDate(i);
                value = date == null ? null : date.toLocalDate().toString();
            } else {
                value = result.getObject(i);
                if (value instanceof java.util.Date) value = value.toString();
            }
            fields.put(name, value);
        }
        return new ExtractionRecord(fields, result.getBoolean("_invalid"), result.getBoolean("_unmatched"));
    }

    protected SQLException safeFailure(String entity, SQLException error) {
        // Do not attach driver exceptions: their messages may contain source values or credentials.
        return new SQLException("Unable to extract " + entity + ". Verify schema, permissions and query timeout.",
                                error.getSQLState());
    }
}
