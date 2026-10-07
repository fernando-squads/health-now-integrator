package br.com.bancadoingresso.integrator.util;

import br.com.bancadoingresso.integrator.model.DatabaseModel;

public class DatabaseProperties {
	private static DatabaseProperties instance;
	private DatabaseModel model;
	
	private DatabaseProperties() {

	}
	
	private DatabaseProperties(String url, String port, String database, String user, String pwd) {
		model = new DatabaseModel(url, port, database, user, pwd);
	}
	
	
	public static synchronized void creatInstance(String url, String port, String database, String user, String pwd) {
		instance = new DatabaseProperties(url, port, database, user, pwd);
	}
	
	public static synchronized DatabaseProperties getInstance() {
		return instance;
	}
	
	public DatabaseModel getModel() {
		return model;
	}
}
