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

	public static synchronized Connection getConnection() {
		try {
			if (con == null) {
				logger.log(Level.INFO, "Get connection with Database...");
				DatabaseModel model = DatabaseProperties.getInstance().getModel();
				Class.forName("org.postgresql.Driver");
				String connextionURL = "jdbc:postgresql://" + model.getUrl() + ":" + model.getPort() + "/" + model.getDatabase();
				con = DriverManager.getConnection(connextionURL, model.getUser(), model.getPwd());
				logger.log(Level.INFO, "Get connection with Database... (OK)");
			}
		} catch (ClassNotFoundException e) {
			logger.log(Level.SEVERE, "Error getting database connection", e);
			System.exit(-1);
		} catch (SQLException e) {
			logger.log(Level.SEVERE, "Error getting database connection", e);
			System.exit(-1);
		}
		return con;
	}
}
