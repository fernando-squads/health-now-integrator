package br.com.bancadoingresso.integrator.model;

public class Cidadao {
	private Long coSeqCidadao;
	private Integer stDesconheceNomeMae;
	private Localidade localidade;
    
    public Cidadao() {
    	
    }

	public Long getCoSeqCidadao() {
		return coSeqCidadao;
	}

	public void setCoSeqCidadao(Long coSeqCidadao) {
		this.coSeqCidadao = coSeqCidadao;
	}

	public Integer getStDesconheceNomeMae() {
		return stDesconheceNomeMae;
	}

	public void setStDesconheceNomeMae(Integer stDesconheceNomeMae) {
		this.stDesconheceNomeMae = stDesconheceNomeMae;
	}

	public Localidade getLocalidade() {
		return localidade;
	}

	public void setLocalidade(Localidade localidade) {
		this.localidade = localidade;
	}
}
