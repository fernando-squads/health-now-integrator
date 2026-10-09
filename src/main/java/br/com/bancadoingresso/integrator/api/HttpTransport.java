package br.com.bancadoingresso.integrator.api;

import com.google.gson.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Map;

/** No redirect following, raw response logging or credential-bearing exception causes. */
public final class HttpTransport {
    private final boolean localTests;
    public HttpTransport() { this(false); }
    HttpTransport(boolean localTests) { this.localTests = localTests; }
    public static final class Failure extends IOException {
        private static final long serialVersionUID = 1L;
        public final int status;
        Failure(int status, String message) {
            super(message == null ? "A operação HTTP não foi confirmada (status " + status + ")."
                : "A operação HTTP não foi confirmada (status " + status + "): " + message);
            this.status = status;
        }
    }
    public URI base(String value) throws IOException {
        try {
            URI uri = new URI(value);
            validate(uri, false);
            String path = uri.getPath();
            if (uri.getRawQuery() != null || !(path == null || path.isEmpty() || "/".equals(path))) throw new IOException();
            return uri.resolve("/");
        } catch (Exception e) { throw new IOException("A URL HTTP ou HTTPS configurada para a API é inválida. Use uma origem sem caminho, credenciais ou parâmetros."); }
    }
    private void validate(URI uri, boolean upload) throws IOException {
        boolean loopback = localTests && "http".equals(uri.getScheme()) && "127.0.0.1".equals(uri.getHost());
        boolean apiOrigin = !upload && ("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()));
        boolean secureUpload = upload && "https".equals(uri.getScheme());
        if ((!apiOrigin && !secureUpload && !loopback) || uri.getHost() == null
                || uri.getUserInfo() != null || uri.getFragment() != null) throw new IOException("Destino HTTP inválido.");
        if (upload && !loopback && !uri.getHost().matches("[a-z0-9.-]+\\.s3(?:\\.[a-z0-9-]+)?\\.amazonaws\\.com")) {
            throw new IOException("O destino de upload não é um endpoint S3 autorizado.");
        }
    }
    private HttpURLConnection open(URI uri, String method, boolean upload) throws IOException {
        validate(uri, upload);
        HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
        connection.setInstanceFollowRedirects(false);
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(60000);
        connection.setUseCaches(false);
        connection.setRequestMethod(method);
        return connection;
    }
    public JsonObject json(URI base, String method, String path, JsonObject body,
                           String token, String operation, String idempotency) throws IOException {
        HttpURLConnection connection = null;
        try {
            URI uri = base.resolve(path);
            if (!uri.getAuthority().equals(base.getAuthority()) || !uri.getScheme().equals(base.getScheme())) throw new IOException();
            connection = open(uri, method, false);
            connection.setRequestProperty("Accept", "application/json");
            if (token != null) connection.setRequestProperty("Authorization", "Bearer " + token);
            if (operation != null) connection.setRequestProperty("X-Operation-Id", operation);
            if (idempotency != null) connection.setRequestProperty("Idempotency-Key", idempotency);
            if (body != null) {
                byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setDoOutput(true);
                connection.setFixedLengthStreamingMode(bytes.length);
                try (OutputStream output = connection.getOutputStream()) { output.write(bytes); }
            }
            int status = connection.getResponseCode();
            if (status < 200 || status > 299) throw failure(connection, status);
            if (status == 204) return new JsonObject();
            String type = connection.getContentType();
            if (type == null || !type.split(";", 2)[0].trim().equalsIgnoreCase("application/json")) throw new IOException();
            try (InputStream input = connection.getInputStream()) {
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                byte[] bytes = new byte[4096]; int count;
                while ((count = input.read(bytes)) != -1) {
                    if (buffer.size() + count > 65536) throw new IOException();
                    buffer.write(bytes, 0, count);
                }
                return JsonParser.parseString(new String(buffer.toByteArray(), StandardCharsets.UTF_8)).getAsJsonObject();
            }
        } catch (Failure e) { throw e;
        } catch (Exception e) { throw new IOException("Falha de comunicação segura com a API. Repita a operação sem gerar outro arquivo.");
        } finally { if (connection != null) connection.disconnect(); }
    }
    public void upload(Path archive, long size, URI uri, Map<String, String> headers) throws IOException {
        HttpURLConnection connection = null;
        try {
            connection = open(uri, "PUT", true);
            for (Map.Entry<String, String> header : headers.entrySet()) {
                if (!"content-length".equalsIgnoreCase(header.getKey())) connection.setRequestProperty(header.getKey(), header.getValue());
            }
            connection.setDoOutput(true);
            connection.setFixedLengthStreamingMode(size);
            try (InputStream input = Files.newInputStream(archive); OutputStream output = connection.getOutputStream()) {
                byte[] buffer = new byte[65536]; int count;
                while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            }
            int status = connection.getResponseCode();
            // 412 is only an existing-object candidate. API HEAD must still verify it.
            if (status != 200 && status != 201 && status != 204 && status != 412) throw failure(connection, status);
        } catch (Failure e) { throw e;
        } catch (Exception e) { throw new IOException("Upload não confirmado. Preserve o arquivo para retomada.");
        } finally { if (connection != null) connection.disconnect(); }
    }
    private Failure failure(HttpURLConnection connection, int status) {
        String message = null;
        try {
            String type = connection.getContentType();
            if (type != null && type.split(";", 2)[0].trim().equalsIgnoreCase("application/json")) {
                InputStream stream = connection.getErrorStream();
                if (stream != null) {
                    try (InputStream input = stream) {
                        JsonObject body = JsonParser.parseString(new String(read(input), StandardCharsets.UTF_8)).getAsJsonObject();
                        message = publicMessage(body);
                    }
                }
            }
        } catch (Exception ignored) { /* Unknown error payloads must not be displayed. */ }
        return new Failure(status, message);
    }
    private byte[] read(InputStream input) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] bytes = new byte[4096]; int count;
        while ((count = input.read(bytes)) != -1) {
            if (buffer.size() + count > 65536) throw new IOException();
            buffer.write(bytes, 0, count);
        }
        return buffer.toByteArray();
    }
    private String publicMessage(JsonObject body) {
        if (!body.has("message") || !body.get("message").isJsonPrimitive()
                || !body.getAsJsonPrimitive("message").isString()) return null;
        StringBuilder message = new StringBuilder(sanitize(body.get("message").getAsString(), 400));
        if (message.length() == 0) return null;
        if (body.has("details") && body.get("details").isJsonArray()) {
            for (JsonElement detail : body.getAsJsonArray("details")) {
                if (!detail.isJsonPrimitive() || !detail.getAsJsonPrimitive().isString()) continue;
                String value = sanitize(detail.getAsString(), 120);
                if (value.length() == 0) continue;
                if (message.length() + value.length() + 2 > 512) break;
                message.append(": ").append(value);
            }
        }
        return message.toString();
    }
    private String sanitize(String value, int maximum) {
        StringBuilder result = new StringBuilder();
        for (int index = 0; index < value.length() && result.length() < maximum; index++) {
            char current = value.charAt(index);
            if (!Character.isISOControl(current)) result.append(current);
        }
        return result.toString().trim();
    }
}
