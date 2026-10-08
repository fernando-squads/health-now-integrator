package br.com.bancadoingresso.integrator.api;

import java.io.*;
import java.net.URI;
import java.nio.file.*;
import java.security.*;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.spec.PKCS8EncodedKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.*;
import br.com.bancadoingresso.integrator.util.ProtectedFiles;

/** Locally created password-protected JCEKS; never shipped inside the application. */
public final class InstallationKey {
    private final PrivateKey key;
    public final String installationId, publicKey, fingerprint;
    public final URI api;
    private InstallationKey(PrivateKey key, String installationId, URI api) throws Exception {
        this.key = key; this.installationId = installationId; this.api = api;
        RSAPrivateCrtKey rsa = (RSAPrivateCrtKey) key;
        ByteArrayOutputStream integers = new ByteArrayOutputStream();
        integers.write(der(2, rsa.getModulus().toByteArray()));
        integers.write(der(2, rsa.getPublicExponent().toByteArray()));
        byte[] encoded = der(48, integers.toByteArray());
        this.publicKey = Base64.getEncoder().encodeToString(encoded);
        this.fingerprint = hash(encoded);
    }
    public static synchronized InstallationKey open(Path file, char[] password, String installation, URI api) throws IOException {
        byte[] encoded = null;
        try {
            if (password == null || password.length < 12 || !UUID.fromString(installation).toString().equals(installation)) throw new IOException();
            file = file.toAbsolutePath().normalize();
            Files.createDirectories(file.getParent());
            if (Files.isSymbolicLink(file.getParent())) throw new IOException();
            try (java.nio.channels.FileChannel lockChannel = java.nio.channels.FileChannel.open(file.resolveSibling(file.getFileName() + ".lock"),
                     StandardOpenOption.CREATE, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS);
                 java.nio.channels.FileLock lock = lockChannel.tryLock()) {
            if (lock == null) throw new IOException();
            KeyStore store = KeyStore.getInstance("JCEKS");
            if (Files.exists(file, LinkOption.NOFOLLOW_LINKS)) {
                if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || Files.size(file) > 65536) throw new IOException();
                ProtectedFiles.restrict(file);
                try (InputStream input = Files.newInputStream(file)) { store.load(input, password); }
                String binding = new String(store.getKey("binding", password).getEncoded(), StandardCharsets.UTF_8);
                if (!(installation + "\n" + api.toString()).equals(binding)) throw new IOException();
                encoded = KeyEnvelope.open(store.getKey("identity-pkcs8", password).getEncoded(), password, binding);
            } else {
                store.load(null, password);
                KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA"); generator.initialize(3072);
                encoded = generator.generateKeyPair().getPrivate().getEncoded();
                // SecretKeyEntry protects opaque PKCS#8 bytes without a fake X.509 certificate.
                byte[] envelope = KeyEnvelope.protect(encoded, password, installation + "\n" + api);
                store.setEntry("identity-pkcs8", new KeyStore.SecretKeyEntry(new SecretKeySpec(envelope, "RAW")), new KeyStore.PasswordProtection(password));
                Arrays.fill(envelope, (byte)0);
                store.setEntry("binding", new KeyStore.SecretKeyEntry(new SecretKeySpec((installation + "\n" + api).getBytes(StandardCharsets.UTF_8), "RAW")), new KeyStore.PasswordProtection(password));
                Path temporary = Files.createTempFile(file.getParent(), ".identity-", ".pending");
                try {
                    ProtectedFiles.restrict(temporary);
                    ByteArrayOutputStream protectedStore = new ByteArrayOutputStream();
                    store.store(protectedStore, password);
                    try (java.nio.channels.FileChannel channel = java.nio.channels.FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                        java.nio.ByteBuffer buffer = java.nio.ByteBuffer.wrap(protectedStore.toByteArray());
                        while (buffer.hasRemaining()) channel.write(buffer);
                        channel.force(true);
                    }
                    Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE);
                } finally { Files.deleteIfExists(temporary); }
            }
            return new InstallationKey(KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(encoded)), installation, api);
            }
        } catch (Exception e) { throw new IOException("Não foi possível abrir a identidade local. Verifique senha, instalação, API e permissões do keystore (" + e.getClass().getSimpleName() + ").");
        } finally { if (encoded != null) Arrays.fill(encoded, (byte) 0); }
    }
    public String sign(String message) throws IOException {
        try {
            Signature signature = Signature.getInstance("SHA256withRSA"); signature.initSign(key);
            signature.update(message.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(signature.sign());
        } catch (GeneralSecurityException e) { throw new IOException("Não foi possível assinar a solicitação da instalação."); }
    }
    PublicKey verificationKey() throws GeneralSecurityException {
        RSAPrivateCrtKey rsa = (RSAPrivateCrtKey) key;
        return KeyFactory.getInstance("RSA").generatePublic(new java.security.spec.RSAPublicKeySpec(rsa.getModulus(), rsa.getPublicExponent()));
    }
    public static String hash(byte[] bytes) {
        try {
            StringBuilder result = new StringBuilder();
            for (byte b : MessageDigest.getInstance("SHA-256").digest(bytes)) result.append(String.format(Locale.ROOT, "%02x", b & 255));
            return result.toString();
        } catch (GeneralSecurityException e) { throw new IllegalStateException("SHA-256 unavailable"); }
    }
    private static byte[] der(int tag, byte[] value) throws IOException {
        ByteArrayOutputStream result = new ByteArrayOutputStream(); result.write(tag);
        if (value.length < 128) result.write(value.length);
        else if (value.length < 256) { result.write(129); result.write(value.length); }
        else { result.write(130); result.write(value.length >> 8); result.write(value.length & 255); }
        result.write(value); return result.toByteArray();
    }
}
