package br.com.bancadoingresso.integrator.model;

public class Uf {
	private Long coUf;
	private Pais pais;
	private String sgUf;
	private String nuDne;
	private String noUf;
	private String noIdentificador;
	
	public Uf() {
		
	}

	public Long getCoUf() {
		return coUf;
	}

	public void setCoUf(Long coUf) {
		this.coUf = coUf;
	}

	public Pais getPais() {
		return pais;
	}

	public void setPais(Pais pais) {
		this.pais = pais;
	}

	public String getSgUf() {
		return sgUf;
	}

	public void setSgUf(String sgUf) {
		this.sgUf = sgUf;
	}

	public String getNuDne() {
		return nuDne;
	}

	public void setNuDne(String nuDne) {
		this.nuDne = nuDne;
	}

	public String getNoUf() {
		return noUf;
	}

	public void setNoUf(String noUf) {
		this.noUf = noUf;
	}

	public String getNoIdentificador() {
		return noIdentificador;
	}

	public void setNoIdentificador(String noIdentificador) {
		this.noIdentificador = noIdentificador;
	}
}