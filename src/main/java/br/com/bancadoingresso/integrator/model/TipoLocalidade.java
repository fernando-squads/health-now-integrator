package br.com.bancadoingresso.integrator.model;

public class TipoLocalidade {
	private Long coTipoLocalidade;
	private String noTipoLocalidade;
	private String sgTipoLocalidade;
	private String noIdentificador;
	
	public TipoLocalidade() {
		
	}

	public Long getCoTipoLocalidade() {
		return coTipoLocalidade;
	}

	public void setCoTipoLocalidade(Long coTipoLocalidade) {
		this.coTipoLocalidade = coTipoLocalidade;
	}

	public String getNoTipoLocalidade() {
		return noTipoLocalidade;
	}

	public void setNoTipoLocalidade(String noTipoLocalidade) {
		this.noTipoLocalidade = noTipoLocalidade;
	}

	public String getSgTipoLocalidade() {
		return sgTipoLocalidade;
	}

	public void setSgTipoLocalidade(String sgTipoLocalidade) {
		this.sgTipoLocalidade = sgTipoLocalidade;
	}

	public String getNoIdentificador() {
		return noIdentificador;
	}

	public void setNoIdentificador(String noIdentificador) {
		this.noIdentificador = noIdentificador;
	}
}
