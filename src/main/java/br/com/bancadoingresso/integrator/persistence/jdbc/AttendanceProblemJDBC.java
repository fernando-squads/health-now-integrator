package br.com.bancadoingresso.integrator.persistence.jdbc;

import java.sql.Connection;
import java.util.Arrays;
import java.util.List;

public final class AttendanceProblemJDBC extends ExtractionRepository {
    public AttendanceProblemJDBC(Connection connection, int timeoutSeconds) { super(connection, timeoutSeconds); }

    @Override
    public List<ExtractionQuery> queries() {
        return Arrays.asList(
            new ExtractionQuery("attendance-problem", "attendance-problem/find-page.sql", "tb_fat_atd_ind_problemas"));
    }
}
