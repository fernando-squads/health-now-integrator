package br.com.bancadoingresso.integrator.persistence;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

import br.com.bancadoingresso.integrator.model.DatabaseModel;
import br.com.bancadoingresso.integrator.util.DatabaseProperties;

public class ConnectionFactory {
	private static Logger logger = Logger.getLogger(ConnectionFactory.class.getName());
	private static Connection con;

	private ConnectionFactory() {

	}

	public static synchronized Connection getConnection() throws SQLException {
		try {
			if (con == null || con.isClosed()) {
				logger.log(Level.INFO, "Get connection with Database...");
				DatabaseProperties properties = DatabaseProperties.getInstance();
				if (properties == null || properties.getModel() == null) {
					throw new SQLException("Database connection properties have not been configured.");
				}
				DatabaseModel model = properties.getModel();
				Class.forName("org.postgresql.Driver");
				String connectionUrl = "jdbc:postgresql://" + model.getUrl() + ":" + model.getPort() + "/" + model.getDatabase();
				con = DriverManager.getConnection(connectionUrl, model.getUser(), model.getPwd());
				logger.log(Level.INFO, "Get connection with Database... (OK)");
			}
		} catch (ClassNotFoundException e) {
			logger.log(Level.SEVERE, "Error getting database connection", e);
			throw new SQLException("PostgreSQL JDBC driver was not found.", e);
		} catch (SQLException e) {
			logger.log(Level.SEVERE, "Error getting database connection. SQL state: {0}", e.getSQLState());
			throw new SQLException("Unable to connect. Verify database settings and availability.", e.getSQLState());
		}
		return con;
	}

    /** A dedicated connection prevents retries from reusing previous form credentials. */
    public static Connection openExtractionConnection() throws SQLException {
        DatabaseProperties properties = DatabaseProperties.getInstance();
        if (properties == null || properties.getModel() == null) {
            throw new SQLException("Database settings have not been configured.");
        }
        DatabaseModel model = properties.getModel();
        java.util.Properties settings = new java.util.Properties();
        settings.setProperty("user", model.getUser());
        settings.setProperty("password", model.getPwd());
        settings.setProperty("connectTimeout", "10");
        settings.setProperty("socketTimeout", "120");
        settings.setProperty("readOnly", "true");
        settings.setProperty("readOnlyMode", "always");
        try {
            Class.forName("org.postgresql.Driver");
            return DriverManager.getConnection("jdbc:postgresql://" + model.getUrl() + ":"
                + model.getPort() + "/" + model.getDatabase(), settings);
        } catch (ClassNotFoundException e) {
            throw new SQLException("PostgreSQL JDBC driver was not found.");
        } catch (SQLException e) {
            throw new SQLException("Unable to connect. Verify database settings and availability.", e.getSQLState());
        }
    }
}
