package br.com.bancadoingresso.integrator.service;

import com.google.gson.*;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;

/** Updated atomically, only after the corresponding external step is confirmed. */
final class DeliveryJournal {
    private final Path path;
    private final JsonObject data;

    DeliveryJournal(LoadArtifact artifact, String storage, String api) throws IOException {
        path = artifact.path.resolveSibling("delivery.json");
        if (Files.exists(path)) {
            try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                data = JsonParser.parseReader(reader).getAsJsonObject();
                if (!artifact.sha256.equals(data.get("sha256").getAsString())
                        || !artifact.fileId.equals(data.get("file_id").getAsString())
                        || !artifact.runId.equals(data.get("run_id").getAsString())
                        || !artifact.installationId.equals(data.get("installation_id").getAsString())
                        || !storage.equals(data.get("storage").getAsString())
                        || !api.equals(data.get("api").getAsString())
                        || !("created".equals(state()) || "uploaded".equals(state()) || "registered".equals(state()))) {
                    throw new IOException("Delivery state conflicts with archive or destination configuration.");
                }
            } catch (RuntimeException e) { throw new IOException("Invalid local delivery state."); }
        } else {
            data = new JsonObject();
            data.addProperty("file_id", artifact.fileId);
            data.addProperty("run_id", artifact.runId);
            data.addProperty("installation_id", artifact.installationId);
            data.addProperty("sha256", artifact.sha256);
            data.addProperty("storage", storage);
            data.addProperty("api", api);
            data.addProperty("idempotency_key", artifact.idempotencyKey());
            save("created", null);
        }
    }

    String state() { return data.get("state").getAsString(); }

    void save(String state, String receipt) throws IOException {
        data.addProperty("state", state);
        data.addProperty("updated_at", Instant.now().toString());
        if (receipt != null) data.addProperty("receipt_id", receipt);
        Path temporary = Files.createTempFile(path.getParent(), ".delivery-", ".tmp");
        try {
            byte[] bytes = new Gson().toJson(data).getBytes(StandardCharsets.UTF_8);
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) channel.write(buffer);
                channel.force(true);
            }
            Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temporary); }
    }
}
