package br.com.bancadoingresso.integrator.model;

public class DatabaseModel {
	private String url;
	private String port;
	private String database;
	private String user;
	private String pwd;
	
	public DatabaseModel() {
		
	}
	
	public DatabaseModel(String url, String port, String database, String user, String pwd) {
		this.url = url;
		this.port = port;
		this.database = database;
		this.user = user;
		this.pwd = pwd;
	}

	public String getUrl() {
		return url;
	}

	public void setUrl(String url) {
		this.url = url;
	}

	public String getPort() {
		return port;
	}

	public void setPort(String port) {
		this.port = port;
	}

	public String getDatabase() {
		return database;
	}

	public void setDatabase(String database) {
		this.database = database;
	}

	public String getUser() {
		return user;
	}

	public void setUser(String user) {
		this.user = user;
	}

	public String getPwd() {
		return pwd;
	}

	public void setPwd(String pwd) {
		this.pwd = pwd;
	}
}
