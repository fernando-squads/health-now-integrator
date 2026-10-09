package br.com.bancadoingresso.integrator.api;
import br.com.bancadoingresso.integrator.service.LoadArtifact;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.time.Instant;
import java.util.*;
public final class FileStatus {
    public final String receipt, state;
    public final Instant expires;
    public FileStatus(JsonObject response, LoadArtifact artifact) throws IOException {
        try {
            state = response.get("state").getAsString();
            if (!Arrays.asList("ready_for_processing", "file_received").contains(state)) throw new IOException();
            receipt = null;
            expires = java.time.OffsetDateTime.parse(response.get("expires_at").getAsString()).toInstant();
        } catch (Exception e) { throw new IOException("Resposta de status incompatível com o arquivo local."); }
    }
    public boolean registered() { return "ready_for_processing".equals(state); }
}
