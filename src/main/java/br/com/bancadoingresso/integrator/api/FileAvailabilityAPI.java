package br.com.bancadoingresso.integrator.api;
import br.com.bancadoingresso.integrator.service.LoadArtifact;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.util.UUID;
public final class FileAvailabilityAPI implements FileDeliveryAPI {
    private final HttpTransport http;
    private final InstallationSessionClient sessions;
    public FileAvailabilityAPI(HttpTransport http, InstallationSessionClient sessions) { this.http = http; this.sessions = sessions; }
    public String destination() { return sessions.identity.api.toString(); }
    public void preflight(String installation, String run) throws IOException {
        if (!sessions.identity.installationId.equals(installation) || run == null) throw new IOException("Informe a instalação ativada e a execução autorizada pelo ADM.");
        try {
            if (!UUID.fromString(run).toString().equals(run)) throw new IOException("Execução inválida.");
            JsonObject result = request("GET", "/integration/v1/esus-imports/" + run, null, null, null);
            if (!run.equals(result.get("run").getAsString()) || !"Receiving".equals(result.get("state").getAsString())) throw new IOException("A execução não está autorizada para receber o arquivo.");
        } catch (RuntimeException e) { throw new IOException("Execução de integração inválida."); }
    }
    public UploadAuthorization authorize(LoadArtifact artifact, String operation, String key) throws IOException {
        JsonObject manifest = artifact.manifest(), body = new JsonObject(); body.addProperty("protocol_version", 1);
        body.addProperty("installation_id", artifact.installationId); body.addProperty("file_id", artifact.fileId);
        body.addProperty("sha256", artifact.sha256); body.addProperty("size_bytes", artifact.size); body.addProperty("content_type", "application/zip");
        for (String field : new String[] {"source_version", "mapping_version", "schema_version", "extraction_cutoff"}) body.add(field, manifest.get(field));
        return new UploadAuthorization(request("POST", path(artifact) + "/upload-authorizations", body, operation, key), artifact);
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
        if (!sessions.identity.installationId.equals(artifact.installationId)) throw new IOException("Arquivo de outra instalação.");
        return "/integration/v1/esus-imports/" + artifact.runId + "/files/" + artifact.fileId;
    }
    private JsonObject request(String method, String path, JsonObject body, String operation, String key) throws IOException {
        for (int attempt = 0; attempt < 2; attempt++) {
            try { return http.json(sessions.identity.api, method, path, body, sessions.token(), operation, key); }
            catch (HttpTransport.Failure failure) {
                if (failure.status != 401 || attempt != 0) throw failure;
                sessions.invalidate();
            }
        }
        throw new IOException("Autenticação da instalação não confirmada.");
    }
}
