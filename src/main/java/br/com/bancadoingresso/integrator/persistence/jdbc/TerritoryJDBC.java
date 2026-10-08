package br.com.bancadoingresso.integrator.persistence.jdbc;

import java.sql.Connection;
import java.util.Arrays;
import java.util.List;

public final class TerritoryJDBC extends ExtractionRepository {
    public TerritoryJDBC(Connection connection, int timeoutSeconds) { super(connection, timeoutSeconds); }

    @Override
    public List<ExtractionQuery> queries() {
        return Arrays.asList(
            new ExtractionQuery("territory", "territory/find-page.sql", "tb_fat_cidadao_territorio"),
            new ExtractionQuery("territory-followup", "territory-followup/find-page.sql", "tb_acomp_cidadaos_vinculados"));
    }
}
