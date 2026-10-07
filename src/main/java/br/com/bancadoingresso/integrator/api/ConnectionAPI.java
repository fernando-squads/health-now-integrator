package br.com.bancadoingresso.integrator.api;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.lang.reflect.ParameterizedType;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Date;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import br.com.bancadoingresso.integrator.util.GsonUTCDateAdapter;

public class ConnectionAPI<S, R> {
	private final Integer UNAUTHORIZED = 401;
	protected R post(String apiURL, Map<String, String> header, S entitySend) throws IOException {
        return makeConnection("POST", apiURL, header, entitySend);
    }

    protected R get(String apiURL, Map<String, String> header) throws IOException {
        return makeConnection("GET", apiURL, header, null);
    }

    protected R put(String apiURL, Map<String, String> header, S entitySend) throws IOException {
        return makeConnection("PUT", apiURL, header, entitySend);
    }

    protected R delete(String apiURL, Map<String, String> header, S entitySend) throws IOException {
        return makeConnection("DELETE", apiURL, header, entitySend);
    }
    
    protected R patch(String apiURL, Map<String, String> header) throws IOException {
        return makeConnection("PATCH", apiURL, header, null);
    }

    private R makeConnection(String connectionType, String apiURL, Map<String, String> header, S entitySend) throws IOException {
    	Gson gson = new GsonBuilder().registerTypeAdapter(Date.class, new GsonUTCDateAdapter()).create();
        String json = null;
        if (entitySend != null) {
        	json = gson.toJson(entitySend);
        }
        URL url = new URL(apiURL);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod(connectionType);
        connection.setRequestProperty("Content-type", "application/json");
        if (header != null && !header.isEmpty()) {
	        for (Entry<String, String> entry : header.entrySet()) {
	            String key = entry.getKey();
	            String value = entry.getValue();
	            connection.setRequestProperty (key, value);
	        }
        }
        
        connection.setDoOutput(true);
        if (json != null) {
        	PrintStream printStream = new PrintStream(connection.getOutputStream());
            printStream.println(json);
        }
        connection.connect();
        Integer responseCode = connection.getResponseCode();
        if (responseCode >= 200 && responseCode <= 300) {
        	InputStreamReader isReader = new InputStreamReader(connection.getInputStream());
            BufferedReader reader = new BufferedReader(isReader);
            StringBuilder sb = new StringBuilder();
            String str;
            while((str = reader.readLine())!= null){
                sb.append(str);
            }
            json = sb.toString();
            R entityReceive = null;
            entityReceive = gson.fromJson(json, ((ParameterizedType) Objects.requireNonNull(getClass().getGenericSuperclass())).getActualTypeArguments()[1]);
            return entityReceive;	
        } else if (responseCode.equals(UNAUTHORIZED)) {
        	throw new IOException("Server returned HTTP response code: 401");        	
        } else {
        	InputStreamReader isReader = new InputStreamReader(connection.getErrorStream());
        	BufferedReader reader = new BufferedReader(isReader);
            StringBuilder sb = new StringBuilder();
            String str;
            while((str = reader.readLine())!= null){
                sb.append(str);
            }
            throw new IOException("REQUEST: " + json + ", RESPONSE: " + sb.toString());
        }
    }
}
