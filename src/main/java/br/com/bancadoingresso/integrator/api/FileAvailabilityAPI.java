package br.com.bancadoingresso.integrator.api;

import br.com.bancadoingresso.integrator.service.LoadArtifact;
import java.io.IOException;

/** Current API has only batch ingestion; /manifest cannot register an S3 file. */
public final class FileAvailabilityAPI implements LoadRegistrar {
    @Override public void validateConfiguration() throws IOException {
        throw unavailable();
    }
    @Override public String destination() { return "unavailable-file-registration"; }
    @Override public String register(LoadArtifact artifact, String objectKey, String idempotencyKey) throws IOException {
        throw unavailable();
    }
    private IOException unavailable() {
        return new IOException("A API ainda não possui um endpoint para registrar arquivos de carga no S3. "
            + "Implemente o contrato de arquivo no health-now-api antes do envio integrado.");
    }
}
