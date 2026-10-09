package br.com.bancadoingresso.integrator.api;

import br.com.bancadoingresso.integrator.service.LoadArtifact;
import com.google.gson.*;
import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.util.*;

/** A short-lived capability; never persist it in a journal. */
public final class UploadAuthorization {
    public final URI url;
    public final Instant expiresAt;
    public final Map<String, String> headers;
    public UploadAuthorization(JsonObject response, LoadArtifact artifact) throws IOException {
        try {
            if (!artifact.fileId.equals(response.get("file_id").getAsString()) || !artifact.runId.equals(response.get("import_id").getAsString())) throw new IOException();
            url = new URI(response.get("upload_url").getAsString());
            if (url.getRawQuery() == null || !"https".equalsIgnoreCase(url.getScheme())) throw new IOException();
            expiresAt = java.time.OffsetDateTime.parse(response.get("expires_at").getAsString()).toInstant();
            if (!expiresAt.isAfter(Instant.now()) || expiresAt.isAfter(Instant.now().plusSeconds(330))) throw new IOException();
            Map<String, String> values = new TreeMap<String, String>(String.CASE_INSENSITIVE_ORDER);
            Set<String> allowed = new HashSet<String>(Arrays.asList("content-type", "content-length", "if-none-match", "x-amz-checksum-sha256",
                "x-amz-meta-file-id", "x-amz-meta-import-id", "x-amz-server-side-encryption"));
            for (Map.Entry<String, JsonElement> header : response.getAsJsonObject("required_headers").entrySet()) {
                String key = header.getKey().toLowerCase(Locale.ROOT), value = header.getValue().getAsString();
                if (!allowed.contains(key) || values.containsKey(key) || value.contains("\r") || value.contains("\n")) throw new IOException();
                values.put(key, value);
            }
            expect(values, "content-type", "application/zip"); expect(values, "content-length", Long.toString(artifact.size));
            expect(values, "if-none-match", "*"); expect(values, "x-amz-server-side-encryption", "AES256");
            expect(values, "x-amz-checksum-sha256", artifact.checksumBase64());
            expect(values, "x-amz-meta-file-id", artifact.fileId); expect(values, "x-amz-meta-import-id", artifact.runId);
            headers = Collections.unmodifiableMap(values);
        } catch (Exception e) { throw new IOException("Autorização de upload inválida ou incompatível com o arquivo local."); }
    }
    private static void expect(Map<String, String> values, String key, String expected) throws IOException {
        if (!expected.equals(values.get(key))) throw new IOException();
    }
}
