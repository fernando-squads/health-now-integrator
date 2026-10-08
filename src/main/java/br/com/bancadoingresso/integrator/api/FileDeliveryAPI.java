package br.com.bancadoingresso.integrator.api;
import br.com.bancadoingresso.integrator.service.LoadArtifact;
import java.io.IOException;
public interface FileDeliveryAPI {
    String destination();
    void preflight(String installation, String run) throws IOException;
    UploadAuthorization authorize(LoadArtifact artifact, String operation, String key) throws IOException;
    FileStatus status(LoadArtifact artifact) throws IOException;
    FileStatus confirm(LoadArtifact artifact, String objectKey, String operation, String key) throws IOException;
}
