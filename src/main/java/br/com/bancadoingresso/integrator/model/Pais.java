package br.com.bancadoingresso.integrator.model;

public class Pais {
	private Long coPais;
	private String sgPais2;
	private String sgPais3;
	private String noPaisPortugues;
	private String noPaisIngles;
	private String noPaisPortuguesFiltro;
	private String coPaisCadsus;
	private Integer stAtivo;
	
	public Pais() {
		
	}

	public Long getCoPais() {
		return coPais;
	}

	public void setCoPais(Long coPais) {
		this.coPais = coPais;
	}

	public String getSgPais2() {
		return sgPais2;
	}

	public void setSgPais2(String sgPais2) {
		this.sgPais2 = sgPais2;
	}

	public String getSgPais3() {
		return sgPais3;
	}

	public void setSgPais3(String sgPais3) {
		this.sgPais3 = sgPais3;
	}

	public String getNoPaisPortugues() {
		return noPaisPortugues;
	}

	public void setNoPaisPortugues(String noPaisPortugues) {
		this.noPaisPortugues = noPaisPortugues;
	}

	public String getNoPaisIngles() {
		return noPaisIngles;
	}

	public void setNoPaisIngles(String noPaisIngles) {
		this.noPaisIngles = noPaisIngles;
	}

	public String getNoPaisPortuguesFiltro() {
		return noPaisPortuguesFiltro;
	}

	public void setNoPaisPortuguesFiltro(String noPaisPortuguesFiltro) {
		this.noPaisPortuguesFiltro = noPaisPortuguesFiltro;
	}

	public String getCoPaisCadsus() {
		return coPaisCadsus;
	}

	public void setCoPaisCadsus(String coPaisCadsus) {
		this.coPaisCadsus = coPaisCadsus;
	}

	public Integer getStAtivo() {
		return stAtivo;
	}

	public void setStAtivo(Integer stAtivo) {
		this.stAtivo = stAtivo;
	}
}