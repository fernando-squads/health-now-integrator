package br.com.bancadoingresso.integrator.service;

import java.util.logging.Level;
import java.util.logging.Logger;

public class S3Service {
	private static final Logger LOGGER = Logger.getLogger(S3Service.class.getName());
	
	public String sendToBucket(byte[] file) {
		String s3FileKey = "";
		LOGGER.log(Level.INFO, "Send file to bucket ...");
		LOGGER.log(Level.INFO, "Send file to bucket ...(OK)");
		return s3FileKey;
	}
}
