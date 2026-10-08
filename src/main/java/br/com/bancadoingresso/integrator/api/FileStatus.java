package br.com.bancadoingresso.integrator.api;
import br.com.bancadoingresso.integrator.service.LoadArtifact;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.time.Instant;
import java.util.*;
public final class FileStatus {
    public final String objectKey, receipt, state;
    public final Instant expires;
    public FileStatus(JsonObject response, LoadArtifact artifact) throws IOException {
        try {
            if (!artifact.fileId.equals(response.get("file_id").getAsString()) || !artifact.runId.equals(response.get("run_id").getAsString())
                    || !artifact.installationId.equals(response.get("installation_id").getAsString()) || !artifact.sha256.equals(response.get("sha256").getAsString())
                    || artifact.size != response.get("size_bytes").getAsBigDecimal().longValueExact()) throw new IOException();
            objectKey = response.get("object_key").getAsString(); UploadAuthorization.validateKey(objectKey, artifact);
            state = response.get("state").getAsString();
            if (!Arrays.asList("created", "upload_authorized", "uploaded_verified", "registered", "pending", "processing", "processed", "processed_with_pending", "failed").contains(state)) throw new IOException();
            receipt = response.has("receipt_id") && !response.get("receipt_id").isJsonNull() ? response.get("receipt_id").getAsString() : null;
            if (receipt != null && !UUID.fromString(receipt).toString().equals(receipt)) throw new IOException();
            if (registered() && receipt == null) throw new IOException();
            expires = java.time.OffsetDateTime.parse(response.get("expires_at").getAsString()).toInstant();
        } catch (Exception e) { throw new IOException("Resposta de status incompatível com o arquivo local."); }
    }
    public boolean registered() { return Arrays.asList("registered", "pending", "processing", "processed", "processed_with_pending").contains(state); }
}
