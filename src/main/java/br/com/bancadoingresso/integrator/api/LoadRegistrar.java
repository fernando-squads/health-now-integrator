package br.com.bancadoingresso.integrator.api;

import br.com.bancadoingresso.integrator.service.LoadArtifact;
import java.io.IOException;

/** Adapter must confirm durable, idempotent registration, never just HTTP delivery. */
public interface LoadRegistrar {
    void validateConfiguration() throws IOException;
    String destination();
    String register(LoadArtifact artifact, String objectKey, String idempotencyKey) throws IOException;
}
