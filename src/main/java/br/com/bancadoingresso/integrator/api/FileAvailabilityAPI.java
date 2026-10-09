package br.com.bancadoingresso.integrator.api;
import br.com.bancadoingresso.integrator.service.LoadArtifact;
import com.google.gson.JsonObject;
import java.io.IOException;
public final class FileAvailabilityAPI implements FileDeliveryAPI {
    private final InstallationClient client;
    public FileAvailabilityAPI(InstallationClient client) { this.client = client; }
    public String destination() { return client.api.toString(); }
    public void preflight(String installation, String run) throws IOException {
        InstallationClient.CurrentRun current = client.current();
        if (!current.importId.equals(run)) throw new IOException("O arquivo pertence a outra importação.");
    }
    public UploadAuthorization authorize(LoadArtifact artifact, String operation, String key) throws IOException {
        JsonObject body = new JsonObject(); body.addProperty("file_id", artifact.fileId);
        body.addProperty("sha256", artifact.sha256); body.addProperty("size_bytes", artifact.size);
        preflight(artifact.installationId, artifact.runId);
        JsonObject response = request("POST", path() + "/upload-authorizations", body, operation, key);
        client.verifyUpload(response);
        return new UploadAuthorization(response, artifact);
    }
    public FileStatus confirm(LoadArtifact artifact, String objectKey, String operation, String key) throws IOException {
        JsonObject body = new JsonObject(); body.addProperty("file_id", artifact.fileId);
        body.addProperty("sha256", artifact.sha256); body.addProperty("size_bytes", artifact.size);
        FileStatus status = new FileStatus(request("PUT", path() + "/availability", body, operation, key), artifact);
        if (!status.registered()) throw new IOException("O registro do arquivo ainda não foi confirmado.");
        return status;
    }
    private String path() {
        return "/integration/v1/esus-imports/bootstrap/current/file";
    }
    private JsonObject request(String method, String path, JsonObject body, String operation, String key) throws IOException {
        return client.request(method, path, body, operation, key);
    }
}
