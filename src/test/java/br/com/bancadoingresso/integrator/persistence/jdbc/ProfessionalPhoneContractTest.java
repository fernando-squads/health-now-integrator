package br.com.bancadoingresso.integrator.persistence.jdbc;

import br.com.bancadoingresso.integrator.extraction.LoadFileWriter;
import org.junit.Assume;
import org.junit.Test;
import java.io.BufferedWriter;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import static org.junit.Assert.*;

/** Synthetic handoff to the Scheduler suite; never connects to PEC. */
public class ProfessionalPhoneContractTest {
    @Test public void extractSyntheticPhonesUsingTheProductionSqlAndWriter() throws Exception {
        Assume.assumeTrue("disposable-docker".equals(System.getenv("POSTGRES_INTEGRATION_TEST_DATABASE")));
        String output = System.getenv("PROFESSIONAL_PHONE_CONTRACT_FILE");
        Assume.assumeTrue(output != null);
        Properties properties = new Properties();
        properties.setProperty("user", "postgres");
        properties.setProperty("password", "postgres");
        try (Connection connection = DriverManager.getConnection("jdbc:postgresql://127.0.0.1:54322/health_now", properties)) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("CREATE TEMP TABLE phone_contract_prof(co_seq_prof bigint, no_civil_profissional varchar(200),nu_cpf varchar(11),nu_cns varchar(15),nu_telefone varchar(512))");
            }
            String synthetic = String.join("", Collections.nCopies(10, "0"));
            List<String> values = Arrays.asList(synthetic,null," \t","invalid");
            try (PreparedStatement statement = connection.prepareStatement("INSERT INTO phone_contract_prof VALUES(?, 'Synthetic professional', NULL, NULL, ?)")) {
                for (int i=0;i<values.size();i++) {
                    statement.setLong(1,5+i); statement.setString(2,values.get(i)); statement.executeUpdate();
                }
            }
            String query = SqlQueryLoader.load("/queries/reference/operational-professional-find-page.sql")
                .replace("public.tb_prof", "phone_contract_prof");
            try (PreparedStatement statement = connection.prepareStatement(query);
                 BufferedWriter writer = new LoadFileWriter().open(Paths.get(output))) {
                statement.setDate(1,java.sql.Date.valueOf("2026-10-13"));
                statement.setLong(2,Long.MIN_VALUE); statement.setInt(3,100);
                try (ResultSet rows = statement.executeQuery()) {
                    int count=0;
                    while(rows.next()) {
                        assertTrue(Objects.equals(values.get(count),rows.getString("nu_telefone")));
                        new LoadFileWriter().record(writer,new ReferenceJDBC(connection,5).read(rows)); count++;
                    }
                    assertEquals(4,count);
                }
            }
        } catch(SQLException error) { throw new SQLException("Synthetic phone contract failed",error.getSQLState()); }
    }
}
