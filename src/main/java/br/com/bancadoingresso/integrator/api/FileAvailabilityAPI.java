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
        if (!current.installation.equals(installation) || !current.run.equals(run)) throw new IOException("O arquivo ou a extração pertence a outra instalação ou execução.");
    }
    public UploadAuthorization authorize(LoadArtifact artifact, String operation, String key) throws IOException {
        JsonObject manifest = artifact.manifest(), body = new JsonObject(); body.addProperty("protocol_version", 1);
        body.addProperty("installation_id", artifact.installationId); body.addProperty("file_id", artifact.fileId);
        body.addProperty("sha256", artifact.sha256); body.addProperty("size_bytes", artifact.size); body.addProperty("content_type", "application/zip");
        for (String field : new String[] {"source_version", "mapping_version", "schema_version", "extraction_cutoff"}) body.add(field, manifest.get(field));
        preflight(artifact.installationId, artifact.runId);
        JsonObject response = request("POST", path(artifact) + "/upload-authorizations", body, operation, key);
        client.verifyUpload(response);
        return new UploadAuthorization(response, artifact);
    }
    public FileStatus status(LoadArtifact artifact) throws IOException {
        try { return new FileStatus(request("GET", path(artifact), null, null, null), artifact); }
        catch (HttpTransport.Failure failure) { if (failure.status == 404) return null; throw failure; }
    }
    public FileStatus confirm(LoadArtifact artifact, String objectKey, String operation, String key) throws IOException {
        JsonObject body = new JsonObject(); body.addProperty("installation_id", artifact.installationId); body.addProperty("file_id", artifact.fileId);
        body.addProperty("object_key", objectKey); body.addProperty("sha256", artifact.sha256); body.addProperty("size_bytes", artifact.size);
        FileStatus status = new FileStatus(request("PUT", path(artifact) + "/availability", body, operation, key), artifact);
        if (!status.registered()) throw new IOException("O registro do arquivo ainda não foi confirmado.");
        return status;
    }
    private String path(LoadArtifact artifact) throws IOException {
        return "/integration/v1/esus-imports/" + artifact.runId + "/files/" + artifact.fileId;
    }
    private JsonObject request(String method, String path, JsonObject body, String operation, String key) throws IOException {
        return client.request(method, path, body, operation, key);
    }
}
