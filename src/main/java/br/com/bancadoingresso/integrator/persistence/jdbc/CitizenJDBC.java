package br.com.bancadoingresso.integrator.persistence.jdbc;

import java.sql.Connection;
import java.util.Arrays;
import java.util.List;

public final class CitizenJDBC extends ExtractionRepository {
    public CitizenJDBC(Connection connection, int timeoutSeconds) { super(connection, timeoutSeconds); }

    @Override
    public List<ExtractionQuery> queries() {
        return Arrays.asList(
            new ExtractionQuery("citizen-history", "citizen-history/find-page.sql", "tb_fat_cidadao"),
            new ExtractionQuery("citizen", "citizen/find-page.sql", "tb_fat_cidadao_pec"),
            new ExtractionQuery("citizen-source", "citizen-source/find-page.sql", "tb_cidadao"),
            new ExtractionQuery("registration", "registration/find-page.sql", "tb_fat_cad_individual"));
    }

    public br.com.bancadoingresso.integrator.extraction.ExtractionRecord findById(
            java.time.LocalDate cutoff, long id) throws java.sql.SQLException {
        try (java.sql.PreparedStatement statement = prepare("/queries/citizen/find-by-id.sql", cutoff)) {
            statement.setLong(2, id);
            try (java.sql.ResultSet result = statement.executeQuery()) {
                return result.next() ? read(result) : null;
            }
        } catch (java.sql.SQLException error) { throw safeFailure("citizen", error); }
    }
}
