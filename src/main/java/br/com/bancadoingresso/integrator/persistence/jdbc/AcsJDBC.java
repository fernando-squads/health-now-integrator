package br.com.bancadoingresso.integrator.persistence.jdbc;

import java.sql.Connection;
import java.util.Arrays;
import java.util.List;

public final class AcsJDBC extends ExtractionRepository {
    public AcsJDBC(Connection connection, int timeoutSeconds) { super(connection, timeoutSeconds); }

    @Override
    public List<ExtractionQuery> queries() {
        return Arrays.asList(
            new ExtractionQuery("acs", "acs/find-page.sql", "tb_cidadao_nucleo_familiar"));
    }
}
