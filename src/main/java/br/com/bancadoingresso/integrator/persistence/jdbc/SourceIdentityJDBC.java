package br.com.bancadoingresso.integrator.persistence.jdbc;

import java.sql.*;
import java.util.*;

/** Source identity is read in the extraction snapshot; activation is not source identity. */
public final class SourceIdentityJDBC {
    private final Connection connection;
    private final int timeout;

    public SourceIdentityJDBC(Connection connection, int timeout) {
        this.connection = connection;
        this.timeout = timeout;
    }

    public Map<String, Object> read() throws SQLException {
        try {
            String installation = installation();
            List<Map<String, Object>> municipalities = municipalities();
            Map<String, Object> result = new LinkedHashMap<String, Object>();
            result.put("installation_uuid", installation);
            result.put("identity_source", "tb_config_sistema.UUID_APLICACAO");
            result.put("municipalities", municipalities);
            result.put("municipality_source", "tb_unidade_saude.co_localidade_endereco");
            return result;
        } catch (SQLException | IllegalArgumentException error) {
            throw new SQLException("Não foi possível identificar a origem PEC. Verifique UUID_APLICACAO e os vínculos municipais das unidades.",
                error instanceof SQLException ? ((SQLException) error).getSQLState() : null);
        }
    }

    private String installation() throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                SqlQueryLoader.load("/queries/configuration/find-source-identity.sql"))) {
            statement.setQueryTimeout(timeout);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) throw new SQLException("Missing source identity.");
                String value = rows.getString("installation_uuid");
                if (value == null) throw new SQLException("Missing source identity.");
                String canonical = UUID.fromString(value.trim()).toString();
                if (!canonical.equalsIgnoreCase(value.trim()) || new UUID(0, 0).toString().equals(canonical) || rows.next()) {
                    throw new SQLException("Ambiguous or invalid source identity.");
                }
                return canonical;
            }
        }
    }

    private List<Map<String, Object>> municipalities() throws SQLException {
        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        Set<String> codes = new HashSet<String>();
        try (PreparedStatement statement = connection.prepareStatement(
                SqlQueryLoader.load("/queries/configuration/find-source-municipalities.sql"))) {
            statement.setQueryTimeout(timeout);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    String code = rows.getString("ibge_code");
                    String name = rows.getString("name");
                    if (code == null || !code.matches("[0-9]{7}") || name == null || name.trim().isEmpty()
                            || name.codePointCount(0, name.length()) > 255 || result.size() >= 10000
                            || name.codePoints().anyMatch(Character::isISOControl) || !codes.add(code)) {
                        throw new SQLException("Invalid source municipality.");
                    }
                    Map<String, Object> municipality = new LinkedHashMap<String, Object>();
                    municipality.put("ibge_code", code);
                    municipality.put("name", name.trim());
                    result.add(municipality);
                }
            }
        }
        if (result.isEmpty()) throw new SQLException("Missing source municipalities.");
        return result;
    }
}
