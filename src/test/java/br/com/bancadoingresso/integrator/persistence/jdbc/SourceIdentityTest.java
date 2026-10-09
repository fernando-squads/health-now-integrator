package br.com.bancadoingresso.integrator.persistence.jdbc;

import java.lang.reflect.Proxy;
import java.sql.*;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class SourceIdentityTest {
    private static final String SOURCE = "d23a3c45-c0e7-43ef-b91a-3586d1d1e267";

    private SourceIdentityJDBC repository(String[] identities, String[][] municipalities) {
        Connection connection = (Connection) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Connection.class},
            (proxy, method, args) -> {
                boolean identity = args[0].toString().contains("UUID_APLICACAO");
                int[] index = {-1};
                ResultSet result = (ResultSet) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{ResultSet.class},
                    (p, m, a) -> {
                        if ("next".equals(m.getName())) return ++index[0] < (identity ? identities.length : municipalities.length);
                        if ("getString".equals(m.getName())) return identity ? identities[index[0]]
                            : municipalities[index[0]]["ibge_code".equals(a[0]) ? 0 : 1];
                        return null;
                    });
                return Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{PreparedStatement.class},
                    (p, m, a) -> "executeQuery".equals(m.getName()) ? result : null);
            });
        return new SourceIdentityJDBC(connection, 30);
    }

    @Test public void sameSourceRemainsStableAndMunicipalitiesAreExplicit() throws Exception {
        String[][] municipalities = {{"3550308", "Synthetic city"}, {"3304557", "Another synthetic city"}};
        Map<String, Object> first = repository(new String[]{SOURCE}, municipalities).read();
        Map<String, Object> second = repository(new String[]{SOURCE}, municipalities).read();
        assertEquals(first, second);
        assertEquals(SOURCE, first.get("installation_uuid"));
        assertEquals(2, ((List<?>) first.get("municipalities")).size());
        assertFalse(first.containsKey("run_id"));
    }

    @Test public void missingInvalidNilOrAmbiguousSourceNeverFallsBackToRunId() throws Exception {
        for (String[] identities : new String[][]{{}, {null}, {"secret-bad-identifier"},
                {"00000000-0000-0000-0000-000000000000"}, {SOURCE, SOURCE}}) {
            try { repository(identities, new String[][]{{"3550308", "Synthetic"}}).read(); fail(); }
            catch (SQLException expected) {
                assertFalse(expected.getMessage().contains("secret-bad-identifier"));
                assertNull(expected.getCause());
            }
        }
    }

    @Test public void missingOrMalformedMunicipalAssociationFailsClosed() throws Exception {
        for (String[][] municipalities : new String[][][]{{}, {{null, "Synthetic"}},
                {{"355030", "Synthetic"}}, {{"3550308", " "}}}) {
            try { repository(new String[]{SOURCE}, municipalities).read(); fail(); }
            catch (SQLException expected) { assertNull(expected.getCause()); }
        }
    }
}
