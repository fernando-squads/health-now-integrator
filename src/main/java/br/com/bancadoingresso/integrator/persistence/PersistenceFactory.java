package br.com.bancadoingresso.integrator.persistence;

import java.sql.SQLException;

import br.com.bancadoingresso.integrator.persistence.jdbc.CidadaoJDBC;

public class PersistenceFactory {
	private static PersistenceFactory instance;
	private CidadaoJDBC cidadaoJDBC;
	
	private PersistenceFactory() {
		
	}
	
	public static synchronized PersistenceFactory getInstance() {
		if(instance == null) {
			instance = new PersistenceFactory();
		}
		return instance;
	}
	
	public CidadaoJDBC getCidadaoJDBC() throws SQLException {
		if(cidadaoJDBC == null) {
			cidadaoJDBC = new CidadaoJDBC();
		}
		return cidadaoJDBC;
	}
}
