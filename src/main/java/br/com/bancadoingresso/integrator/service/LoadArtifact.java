package br.com.bancadoingresso.integrator.service;

import br.com.bancadoingresso.integrator.extraction.LoadFileWriter;
import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

/** Verified immutable archive; identity is read from the manifest inside the ZIP. */
public final class LoadArtifact {
    public final Path path;
    public final String fileId, runId, installationId, sha256;
    public final long size;
    private final JsonObject manifest;

    private LoadArtifact(Path path, JsonObject manifest, String sha256, long size) {
        this.path = path;
        this.manifest = manifest;
        this.fileId = manifest.get("file_id").getAsString();
        this.runId = manifest.get("run_id").getAsString();
        this.installationId = manifest.get("installation_id").getAsString();
        this.sha256 = sha256;
        this.size = size;
    }

    public static LoadArtifact read(Path archive) throws IOException {
        Path path = archive.toAbsolutePath().normalize();
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
                || path.getParent().getFileName().toString().endsWith(".partial")) {
            throw new IOException("A completed local archive is required.");
        }
        Map<String, Object> bytes = new LoadFileWriter().metadata(path);
        try (ZipFile zip = new ZipFile(path.toFile());
             Reader receiptReader = Files.newBufferedReader(path.resolveSibling("archive.json"), StandardCharsets.UTF_8)) {
            ZipEntry entry = zip.getEntry("manifest.json");
            if (entry == null) throw new IOException("Archive manifest is missing.");
            JsonObject manifest;
            try (InputStream input = zip.getInputStream(entry)) {
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                byte[] chunk = new byte[8192];
                int count;
                while ((count = input.read(chunk)) != -1) {
                    if (buffer.size() + count > 2 * 1024 * 1024) throw new IOException("Archive manifest exceeds the limit.");
                    buffer.write(chunk, 0, count);
                }
                manifest = JsonParser.parseString(new String(buffer.toByteArray(), StandardCharsets.UTF_8)).getAsJsonObject();
            }
            JsonObject receipt = JsonParser.parseReader(receiptReader).getAsJsonObject();
            for (String key : Arrays.asList("file_id", "run_id")) {
                String id = manifest.get(key).getAsString();
                if (!UUID.fromString(id).toString().equals(id) || !id.equals(receipt.get(key).getAsString())) {
                    throw new IOException("Archive identity mismatch.");
                }
            }
            for (String key : Arrays.asList("installation_id", "scope", "extraction_cutoff", "source_version", "mapping_version", "schema_version")) {
                if (manifest.get(key).getAsString().trim().isEmpty()) throw new IOException("Incomplete archive manifest.");
            }
            long size = ((Number) bytes.get("size_bytes")).longValue();
            String hash = bytes.get("sha256").toString();
            if (!hash.equals(receipt.get("sha256").getAsString()) || size != receipt.get("size_bytes").getAsLong()
                    || !path.getFileName().toString().equals(receipt.get("name").getAsString())) {
                throw new IOException("Archive integrity mismatch. Regenerate the load.");
            }
            return new LoadArtifact(path, manifest, hash, size);
        } catch (RuntimeException e) {
            throw new IOException("Invalid archive metadata.");
        }
    }

    public JsonObject manifest() { return manifest.deepCopy(); }

    public String checksumBase64() {
        byte[] hash = new byte[32];
        for (int i = 0; i < hash.length; i++) hash[i] = (byte) Integer.parseInt(sha256.substring(i * 2, i * 2 + 2), 16);
        return Base64.getEncoder().encodeToString(hash);
    }

    public String idempotencyKey() { return installationId + ":" + runId + ":" + fileId + ":" + sha256; }
}
