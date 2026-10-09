package br.com.bancadoingresso.integrator.service;
import com.google.gson.*;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.UUID;

/** Only immutable identity and non-secret recovery metadata may be persisted here. */
final class DeliveryJournal {
    private final Path path;
    private final JsonObject data;
    DeliveryJournal(LoadArtifact artifact, String api) throws IOException {
        path = artifact.path.resolveSibling("delivery.json");
        if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || Files.size(path) > 16384) throw new IOException("Journal local inválido.");
            try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                data = JsonParser.parseReader(reader).getAsJsonObject();
                if (data.get("protocol").getAsBigDecimal().intValueExact() != 2 || !artifact.fileId.equals(data.get("file_id").getAsString())
                    || !artifact.runId.equals(data.get("run_id").getAsString()) || !artifact.installationId.equals(data.get("installation_id").getAsString())
                    || !artifact.sha256.equals(data.get("sha256").getAsString()) || artifact.size != data.get("size_bytes").getAsBigDecimal().longValueExact()
                    || !api.equals(data.get("api").getAsString()) || generation() < 0) throw new IOException("Journal conflita com o arquivo ou a API.");
            } catch (RuntimeException e) { throw new IOException("Journal incompatível. Preserve o arquivo e revise a configuração."); }
        } else {
            data = new JsonObject(); data.addProperty("protocol", 2); data.addProperty("file_id", artifact.fileId);
            data.addProperty("run_id", artifact.runId); data.addProperty("installation_id", artifact.installationId);
            data.addProperty("sha256", artifact.sha256); data.addProperty("size_bytes", artifact.size); data.addProperty("api", api);
            data.addProperty("generation", 0); save("created", null);
        }
    }
    private int generation() { return data.get("generation").getAsBigDecimal().intValueExact(); }
    String key(String purpose) {
        String generation = "authorize".equals(purpose) ? Integer.toString(generation()) : "0";
        return br.com.bancadoingresso.integrator.util.Digests.sha256((data.get("installation_id").getAsString() + ":" + data.get("run_id").getAsString() + ":"
            + data.get("file_id").getAsString() + ":" + data.get("sha256").getAsString() + ":" + purpose + ":" + generation).getBytes(StandardCharsets.UTF_8));
    }
    String operation(String purpose) { return UUID.nameUUIDFromBytes(key(purpose).getBytes(StandardCharsets.UTF_8)).toString(); }
    void renew() throws IOException {
        if (generation() == Integer.MAX_VALUE) throw new IOException("Limite de retomadas atingido.");
        data.addProperty("generation", generation() + 1); save("created", null);
    }
    void pin(String key) throws IOException {
        if (data.has("object_key") && !key.equals(data.get("object_key").getAsString())) throw new IOException("A API alterou a chave do arquivo. Envio cancelado.");
        data.addProperty("object_key", key); save(data.get("state").getAsString(), null);
    }
    void save(String state, String receipt) throws IOException {
        data.addProperty("state", state);
        if (receipt != null) data.addProperty("receipt_id", receipt);
        Path temporary = Files.createTempFile(path.getParent(), ".delivery-", ".tmp");
        try {
            br.com.bancadoingresso.integrator.util.ProtectedFiles.restrict(temporary);
            byte[] bytes = data.toString().getBytes(StandardCharsets.UTF_8);
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                ByteBuffer buffer = ByteBuffer.wrap(bytes); while (buffer.hasRemaining()) channel.write(buffer); channel.force(true);
            }
            Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temporary); }
    }
}
