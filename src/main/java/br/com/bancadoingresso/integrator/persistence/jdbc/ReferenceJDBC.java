package br.com.bancadoingresso.integrator.persistence.jdbc;

import java.sql.Connection;
import java.util.Arrays;
import java.util.List;

public final class ReferenceJDBC extends ExtractionRepository {
    public ReferenceJDBC(Connection connection, int timeoutSeconds) { super(connection, timeoutSeconds); }

    @Override
    public List<ExtractionQuery> queries() {
        return Arrays.asList(
            new ExtractionQuery("professional-assignment", "reference/professional-assignment-find-page.sql", "tb_lotacao"),
            new ExtractionQuery("priority", "reference/priority-find-page.sql", "tb_dim_prioridade_cuidado"),
            new ExtractionQuery("professional", "reference/professional-find-page.sql", "tb_dim_profissional"),
            new ExtractionQuery("team", "reference/team-find-page.sql", "tb_dim_equipe"),
            new ExtractionQuery("health-unit", "reference/health-unit-find-page.sql", "tb_dim_unidade_saude"),
            new ExtractionQuery("operational-professional", "reference/operational-professional-find-page.sql", "tb_prof"),
            new ExtractionQuery("operational-team", "reference/operational-team-find-page.sql", "tb_equipe"),
            new ExtractionQuery("operational-health-unit", "reference/operational-health-unit-find-page.sql", "tb_unidade_saude"),
            new ExtractionQuery("cid", "reference/cid-find-page.sql", "tb_dim_cid"),
            new ExtractionQuery("ciap", "reference/ciap-find-page.sql", "tb_dim_ciap"),
            new ExtractionQuery("procedure", "reference/procedure-find-page.sql", "tb_dim_procedimento"),
            new ExtractionQuery("specialty", "reference/specialty-find-page.sql", "tb_dim_especialidade"),
            new ExtractionQuery("problem-status", "reference/problem-status-find-page.sql", "tb_dim_situacao_problema"),
            new ExtractionQuery("sex", "reference/sex-find-page.sql", "tb_dim_sexo"),
            new ExtractionQuery("time", "reference/time-find-page.sql", "tb_dim_tempo"),
            new ExtractionQuery("cbo", "reference/cbo-find-page.sql", "tb_dim_cbo"),
            new ExtractionQuery("referral-risk", "reference/referral-risk-find-page.sql", "tb_dim_classificacao_risc_enc"),
            new ExtractionQuery("attendance-type", "reference/attendance-type-find-page.sql", "tb_dim_tipo_atendimento"),
            new ExtractionQuery("municipality", "reference/municipality-find-page.sql", "tb_dim_municipio"));
    }
}
