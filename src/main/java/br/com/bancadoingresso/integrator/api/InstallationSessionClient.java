package br.com.bancadoingresso.integrator.api;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.time.Instant;
import java.util.UUID;

/** Tokens are held only in memory; every new session proves possession of the local key. */
public final class InstallationSessionClient {
    public final InstallationKey identity;
    private final HttpTransport http;
    private String token;
    private Instant expires = Instant.EPOCH;
    public InstallationSessionClient(InstallationKey identity, HttpTransport http) { this.identity = identity; this.http = http; }
    public void activate(char[] code) throws IOException {
        try {
            String value = new String(code).trim();
            JsonObject request = new JsonObject();
            request.addProperty("installation_id", identity.installationId);
            request.addProperty("activation_code", value);
            request.addProperty("public_key", identity.publicKey);
            request.addProperty("signature", identity.sign("health-now-activation-v1\n" + identity.installationId + "\n" + value + "\n" + identity.publicKey));
            JsonObject response = http.json(identity.api, "POST", "/integration/v1/esus-installations/activate", request, null, null, null);
            if (!identity.installationId.equals(response.get("installation_id").getAsString())
                    || !identity.fingerprint.equals(response.get("fingerprint").getAsString())) throw new IOException();
        } catch (RuntimeException e) { throw new IOException("Resposta de ativação inválida.");
        } finally { java.util.Arrays.fill(code, '\0'); }
    }
    public synchronized String token() throws IOException {
        if (token != null && expires.isAfter(Instant.now().plusSeconds(30))) return token;
        String nonce = UUID.randomUUID().toString(); long timestamp = Instant.now().getEpochSecond();
        JsonObject request = new JsonObject();
        request.addProperty("installation_id", identity.installationId); request.addProperty("fingerprint", identity.fingerprint);
        request.addProperty("timestamp", timestamp); request.addProperty("nonce", nonce);
        request.addProperty("signature", identity.sign("health-now-session-v1\n" + identity.installationId + "\n" + identity.fingerprint + "\n" + timestamp + "\n" + nonce));
        JsonObject response = http.json(identity.api, "POST", "/integration/v1/esus-installations/tokens", request, null, null, null);
        try {
            String candidate = response.get("access_token").getAsString();
            Instant expiration = java.time.OffsetDateTime.parse(response.get("expires_at").getAsString()).toInstant();
            if (!candidate.matches("[A-Za-z0-9_-]{43}") || !"Bearer".equals(response.get("token_type").getAsString())
                    || !expiration.isAfter(Instant.now()) || expiration.isAfter(Instant.now().plusSeconds(930))) throw new IOException();
            token = candidate; expires = expiration; return token;
        } catch (Exception e) { throw new IOException("Resposta de autenticação da instalação inválida."); }
    }
    public synchronized void invalidate() { token = null; expires = Instant.EPOCH; }
}
