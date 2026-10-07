package br.com.bancadoingresso.integrator.service;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import br.com.bancadoingresso.integrator.api.AuthHealthNowAPI;
import br.com.bancadoingresso.integrator.model.AuthRequest;
import br.com.bancadoingresso.integrator.model.AuthResponse;
import br.com.bancadoingresso.integrator.model.Cidadao;
import br.com.bancadoingresso.integrator.persistence.PersistenceFactory;

public class IntegratorService {
	
	private S3Service s3Service;
	
	public IntegratorService() {
		s3Service = new S3Service();
	}
	
	private static final Logger LOGGER = Logger.getLogger(IntegratorService.class.getName());
	
	public void integrate() throws SQLException, IOException {
		LOGGER.log(Level.INFO, "Start Integration Service");
		LOGGER.log(Level.INFO, "Get all cidadaos");
		List<Cidadao> cidadaos = PersistenceFactory.getInstance().getCidadaoJDBC().getAll();
		byte[] cidadaoFile = generateCidadaoFile(cidadaos);
		String s3FileKey = s3Service.sendToBucket(cidadaoFile);
		AuthRequest authRequest = new AuthRequest();
		AuthResponse authResponse = AuthHealthNowAPI.getInstance().auth("", authRequest);
		// send data to 
	}
	
	private byte[] generateCidadaoFile(List<Cidadao> cidadaos) {
		return null;
	}
}
