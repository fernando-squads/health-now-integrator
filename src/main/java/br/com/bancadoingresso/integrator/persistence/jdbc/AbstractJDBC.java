package br.com.bancadoingresso.integrator.persistence.jdbc;

import java.sql.Connection;
import java.sql.SQLException;

import br.com.bancadoingresso.integrator.persistence.ConnectionFactory;

public class AbstractJDBC {
	protected Connection con;
	
	public AbstractJDBC() throws SQLException {
		con = ConnectionFactory.getConnection();
	}
}
