package br.com.bancadoingresso.integrator.persistence.jdbc;

public final class ExtractionQuery {
    public final String entity;
    public final String resource;
    public final String sourceTable;

    public ExtractionQuery(String entity, String resource, String sourceTable) {
        this.entity = entity;
        this.resource = "/queries/" + resource;
        this.sourceTable = "public." + sourceTable;
    }
}
