package br.com.bancadoingresso.integrator.api;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.*;

/** Public verification keys only. HTTPS and response signatures are independent. */
public final class ApiSignatureVerifier {
    private final Map<String, PublicKey> keys;
    ApiSignatureVerifier(Map<String, PublicKey> keys) { this.keys = Collections.unmodifiableMap(new HashMap<String, PublicKey>(keys)); }
    public static ApiSignatureVerifier bundled() throws IOException {
        try (InputStream input = ApiSignatureVerifier.class.getResourceAsStream("/security/api-public-keys.properties")) {
            if (input == null) throw new ActivationRequired();
            Properties values = new Properties(); values.load(input);
            Map<String, PublicKey> keys = new HashMap<String, PublicKey>();
            for (String id : values.stringPropertyNames()) {
                if (!id.matches("[A-Za-z0-9_-]{1,64}")) throw new ActivationRequired();
                PublicKey key = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(values.getProperty(id))));
                int bits = ((RSAPublicKey) key).getModulus().bitLength();
                if (bits < 2048 || bits > 4096) throw new ActivationRequired();
                keys.put(id, key);
            }
            if (keys.isEmpty()) throw new ActivationRequired();
            return new ApiSignatureVerifier(keys);
        } catch (Exception e) { throw new ActivationRequired(); }
    }
    public void verify(JsonObject response, String kind, String... fields) throws IOException {
        try {
            if (!response.get("protocol_version").getAsJsonPrimitive().isNumber()
                    || response.get("protocol_version").getAsBigDecimal().intValueExact() != 1) throw new ActivationRequired();
            String id = text(response, "signing_key_id");
            PublicKey key = keys.get(id); if (key == null) throw new ActivationRequired();
            StringBuilder bytes = new StringBuilder(kind).append("\n1\n").append(id);
            for (String field : fields) bytes.append('\n').append(text(response, field));
            if ("health-now-upload-authorization-v1".equals(kind)) {
                TreeMap<String, String> headers = new TreeMap<String, String>();
                for (Map.Entry<String, JsonElement> h : response.getAsJsonObject("required_headers").entrySet()) {
                    if (!h.getKey().matches("[a-z0-9-]+")) throw new ActivationRequired();
                    headers.put(h.getKey(), text(response.getAsJsonObject("required_headers"), h.getKey()));
                }
                bytes.append('\n').append(headers.size());
                for (Map.Entry<String, String> h : headers.entrySet()) bytes.append('\n').append(h.getKey()).append('\n').append(h.getValue());
            }
            byte[] payload = bytes.toString().getBytes(StandardCharsets.UTF_8);
            String signature = text(response, "signature");
            if (payload.length > 65536 || signature.length() > 1500) throw new ActivationRequired();
            Signature verifier = Signature.getInstance("SHA256withRSA"); verifier.initVerify(key); verifier.update(payload);
            if (!verifier.verify(Base64.getDecoder().decode(signature))) throw new ActivationRequired();
        } catch (Exception e) { throw new ActivationRequired(); }
    }
    static String text(JsonObject object, String field) throws IOException {
        try {
            JsonPrimitive value = object.get(field).getAsJsonPrimitive();
            if (!value.isString()) throw new ActivationRequired();
            String text = value.getAsString();
            if (text.isEmpty() || text.contains("\n") || text.contains("\r")) throw new ActivationRequired();
            return text;
        } catch (RuntimeException e) { throw new ActivationRequired(); }
    }
    static String uuid(JsonObject object, String field) throws IOException {
        String value = text(object, field);
        try { if (!UUID.fromString(value).toString().equals(value) || new UUID(0,0).toString().equals(value)) throw new ActivationRequired(); }
        catch (IllegalArgumentException e) { throw new ActivationRequired(); }
        return value;
    }
}
