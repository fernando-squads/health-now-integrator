package br.com.bancadoingresso.integrator.persistence.jdbc;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;

final class SqlQueryLoader {
	private SqlQueryLoader() {
	}

	static String load(String resourcePath) throws SQLException {
		InputStream inputStream = SqlQueryLoader.class.getResourceAsStream(resourcePath);
		if (inputStream == null) {
			throw new SQLException("SQL query resource was not found: " + resourcePath);
		}

		try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
			StringBuilder sql = new StringBuilder();
			String line;
			while ((line = reader.readLine()) != null) {
				sql.append(line).append(System.lineSeparator());
			}
			return sql.toString();
		} catch (IOException e) {
			throw new SQLException("Unable to load SQL query resource: " + resourcePath, e);
		}
	}
}
