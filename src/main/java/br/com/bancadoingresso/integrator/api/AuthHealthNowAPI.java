package br.com.bancadoingresso.integrator.api;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import br.com.bancadoingresso.integrator.model.AuthRequest;
import br.com.bancadoingresso.integrator.model.AuthResponse;

public class AuthHealthNowAPI extends ConnectionAPI<AuthRequest, AuthResponse> {
	private final String URL = "/store/auth/v1";
	private String baseURL;
	private static AuthHealthNowAPI instance; 
	private AuthHealthNowAPI() throws IOException {
		InputStream is = AuthHealthNowAPI.class.getResourceAsStream("/application.properties"); 
		Properties properties = new Properties();
		properties.load(is);
		String baseURL = properties.getProperty("health-now-api-url");
		this.baseURL = baseURL;
	}
	
	public static AuthHealthNowAPI getInstance() throws IOException {
		if (instance == null) {
			instance = new AuthHealthNowAPI();
		}
		return instance;
	}
	
	public AuthResponse auth(String token, AuthRequest request) throws IOException {
		Map<String, String> header = new HashMap<String, String>();
		header.put("Authorization", token);
		return this.post(baseURL + URL, header, request);
	}
}
