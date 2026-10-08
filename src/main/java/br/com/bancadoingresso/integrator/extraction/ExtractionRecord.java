package br.com.bancadoingresso.integrator.extraction;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Projection of the explicitly selected columns of a versioned SQL resource. */
public final class ExtractionRecord {
    private final Map<String, Object> fields;
    public final boolean invalid;
    public final boolean unmatched;

    public ExtractionRecord(Map<String, Object> fields, boolean invalid, boolean unmatched) {
        this.fields = Collections.unmodifiableMap(new LinkedHashMap<String, Object>(fields));
        this.invalid = invalid;
        this.unmatched = unmatched;
    }

    public long sourceId() { return ((Number) fields.get("source_id")).longValue(); }
    public Map<String, Object> fields() { return fields; }
}
