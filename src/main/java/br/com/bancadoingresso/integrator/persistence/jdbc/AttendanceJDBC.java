package br.com.bancadoingresso.integrator.persistence.jdbc;

import java.sql.Connection;
import java.util.Arrays;
import java.util.List;

public final class AttendanceJDBC extends ExtractionRepository {
    public AttendanceJDBC(Connection connection, int timeoutSeconds) { super(connection, timeoutSeconds); }

    @Override
    public List<ExtractionQuery> queries() {
        return Arrays.asList(
            new ExtractionQuery("attendance", "attendance/find-page.sql", "tb_fat_atendimento_individual"));
    }
}
