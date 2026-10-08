package br.com.bancadoingresso.integrator.persistence.jdbc;

import java.sql.Connection;
import java.util.Arrays;
import java.util.List;

public final class AttendanceProcedureJDBC extends ExtractionRepository {
    public AttendanceProcedureJDBC(Connection connection, int timeoutSeconds) { super(connection, timeoutSeconds); }

    @Override
    public List<ExtractionQuery> queries() {
        return Arrays.asList(
            new ExtractionQuery("attendance-procedure", "attendance-procedure/find-page.sql", "tb_fat_atd_ind_procedimentos"));
    }
}
