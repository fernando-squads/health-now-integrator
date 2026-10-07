package br.com.bancadoingresso.integrator.model;

public class Localidade {
	private Long coLocalidade;
	private Uf uf;
	private String nuDne;
	private String noLocalidade;
	private String nuCep;
	private TipoLocalidade tipoLocalidade;
	private SituacaoLocalidade situacaoLocalidade;
	private String coIbge;
	private String noLocalidadeFiltro;
	private Integer stAtivo;
	
	public Localidade() {
		
	}

	public Long getCoLocalidade() {
		return coLocalidade;
	}

	public void setCoLocalidade(Long coLocalidade) {
		this.coLocalidade = coLocalidade;
	}

	public Uf getUf() {
		return uf;
	}

	public void setUf(Uf uf) {
		this.uf = uf;
	}

	public String getNuDne() {
		return nuDne;
	}

	public void setNuDne(String nuDne) {
		this.nuDne = nuDne;
	}

	public String getNoLocalidade() {
		return noLocalidade;
	}

	public void setNoLocalidade(String noLocalidade) {
		this.noLocalidade = noLocalidade;
	}

	public String getNuCep() {
		return nuCep;
	}

	public void setNuCep(String nuCep) {
		this.nuCep = nuCep;
	}

	public TipoLocalidade getTipoLocalidade() {
		return tipoLocalidade;
	}

	public void setTipoLocalidade(TipoLocalidade tipoLocalidade) {
		this.tipoLocalidade = tipoLocalidade;
	}

	public SituacaoLocalidade getSituacaoLocalidade() {
		return situacaoLocalidade;
	}

	public void setSituacaoLocalidade(SituacaoLocalidade situacaoLocalidade) {
		this.situacaoLocalidade = situacaoLocalidade;
	}

	public String getCoIbge() {
		return coIbge;
	}

	public void setCoIbge(String coIbge) {
		this.coIbge = coIbge;
	}

	public String getNoLocalidadeFiltro() {
		return noLocalidadeFiltro;
	}

	public void setNoLocalidadeFiltro(String noLocalidadeFiltro) {
		this.noLocalidadeFiltro = noLocalidadeFiltro;
	}

	public Integer getStAtivo() {
		return stAtivo;
	}

	public void setStAtivo(Integer stAtivo) {
		this.stAtivo = stAtivo;
	}

}
