package br.com.bancadoingresso.integrator.persistence.jdbc;

import java.lang.reflect.Proxy;
import java.sql.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class SourceVersionTest {
    private SourceVersionJDBC repository(String... versions) {
        final int[] index = {-1};
        ResultSet rows = (ResultSet) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {ResultSet.class},
            (proxy, method, args) -> {
                if ("next".equals(method.getName())) return ++index[0] < versions.length;
                if ("getString".equals(method.getName())) return versions[index[0]];
                return null;
            });
        PreparedStatement statement = (PreparedStatement) Proxy.newProxyInstance(getClass().getClassLoader(),
            new Class<?>[] {PreparedStatement.class}, (proxy, method, args) -> {
                if ("executeQuery".equals(method.getName())) return rows;
                if ("setQueryTimeout".equals(method.getName())) assertEquals(30, args[0]);
                return null;
            });
        Connection connection = (Connection) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {Connection.class},
            (proxy, method, args) -> {
                assertEquals("prepareStatement", method.getName());
                assertTrue(args[0].toString().contains("VERSAOBANCODADOS"));
                return statement;
            });
        return new SourceVersionJDBC(connection, 30);
    }

    @Test public void readsDatabaseVersion() throws Exception {
        assertEquals("5.5.22", repository(" 5.5.22 ").getVersion());
    }

    @Test public void rejectsMissingBlankNullAndAmbiguousVersion() throws Exception {
        for (String[] values : new String[][] {{}, {null}, {" "}, {"5.5.22", "5.5.24"}}) {
            try { repository(values).getVersion(); fail("Invalid version must fail extraction"); }
            catch (SQLException expected) { assertTrue(expected.getMessage().contains("VERSAOBANCODADOS")); }
        }
    }
}
