package br.com.bancadoingresso.integrator.util;
import java.security.*;
import java.util.Locale;
public final class Digests {
    private Digests() {}
    public static String sha256(byte[] bytes) {
        try {
            StringBuilder result = new StringBuilder();
            for (byte b : MessageDigest.getInstance("SHA-256").digest(bytes)) result.append(String.format(Locale.ROOT, "%02x", b & 255));
            return result.toString();
        } catch (GeneralSecurityException e) { throw new IllegalStateException("SHA-256 unavailable"); }
    }
}
