package br.com.bancadoingresso.integrator.persistence.jdbc;

import java.sql.Connection;
import java.util.Arrays;
import java.util.List;

public final class FamilyJDBC extends ExtractionRepository {
    public FamilyJDBC(Connection connection, int timeoutSeconds) { super(connection, timeoutSeconds); }

    @Override
    public List<ExtractionQuery> queries() {
        return Arrays.asList(
            new ExtractionQuery("household", "household/find-page.sql", "tb_fat_cad_domiciliar"),
            new ExtractionQuery("family", "family/find-page.sql", "tb_fat_familia_territorio"),
            new ExtractionQuery("family-registration", "family-registration/find-page.sql", "tb_fat_cad_dom_familia"),
            new ExtractionQuery("family-history", "family-history/find-page.sql", "tb_fat_familia"));
    }
}
