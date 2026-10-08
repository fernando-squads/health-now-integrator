package br.com.bancadoingresso.integrator.persistence.jdbc;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import br.com.bancadoingresso.integrator.model.Cidadao;

public class CidadaoJDBC extends AbstractJDBC {
	private static final Logger LOGGER = Logger.getLogger(CidadaoJDBC.class.getName());
	private static final String FIND_ALL_QUERY = "/queries/cidadao/find-all.sql";
	
	public CidadaoJDBC() throws SQLException {
		super();
	}

	public List<Cidadao> getAll() throws SQLException {
		List<Cidadao> cidadoes = new ArrayList<Cidadao>();
		String sql = SqlQueryLoader.load(FIND_ALL_QUERY);
		try (PreparedStatement ps = con.prepareStatement(sql)){
			ResultSet rs = ps.executeQuery();
			while (rs.next()) {
				Cidadao cidadao = new Cidadao();
				cidadao.setCoSeqCidadao(rs.getLong("co_seq_cidadao"));
				cidadoes.add(cidadao);
			}
			return cidadoes;
		} catch (SQLException e) {
			LOGGER.log(Level.SEVERE, e.getMessage(), e);
			throw e;
		}
	}
}
