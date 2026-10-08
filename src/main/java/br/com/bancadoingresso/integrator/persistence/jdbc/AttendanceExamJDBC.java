package br.com.bancadoingresso.integrator.persistence.jdbc;

import java.sql.Connection;
import java.util.Arrays;
import java.util.List;

public final class AttendanceExamJDBC extends ExtractionRepository {
    public AttendanceExamJDBC(Connection connection, int timeoutSeconds) { super(connection, timeoutSeconds); }

    @Override
    public List<ExtractionQuery> queries() {
        return Arrays.asList(
            new ExtractionQuery("attendance-exam", "attendance-exam/find-page.sql", "tb_fat_atd_ind_exames"));
    }
}
