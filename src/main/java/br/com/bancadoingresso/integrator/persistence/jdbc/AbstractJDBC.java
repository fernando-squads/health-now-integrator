package br.com.bancadoingresso.integrator.persistence.jdbc;

import java.sql.Connection;

import br.com.bancadoingresso.integrator.persistence.ConnectionFactory;

public class AbstractJDBC {
	protected Connection con;
	
	public AbstractJDBC() {
		con = ConnectionFactory.getConnection();
	}
}
