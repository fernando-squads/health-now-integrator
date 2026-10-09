package br.com.bancadoingresso.integrator.api;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.Arrays;
import javax.crypto.*;
import javax.crypto.spec.*;

/** Java 8 AES-GCM token protection; no application-held wrapping secret. */
final class KeyEnvelope {
    private static final int ITERATIONS = 600000;
    private KeyEnvelope() {}
    static byte[] protect(byte[] secret, char[] password, String binding) throws GeneralSecurityException {
        byte[] salt = new byte[16], nonce = new byte[12]; SecureRandom random = new SecureRandom();
        random.nextBytes(salt); random.nextBytes(nonce);
        byte[] ciphertext = crypt(Cipher.ENCRYPT_MODE, secret, password, binding, salt, nonce);
        return ByteBuffer.allocate(1 + salt.length + nonce.length + ciphertext.length).put((byte)1).put(salt).put(nonce).put(ciphertext).array();
    }
    static byte[] open(byte[] envelope, char[] password, String binding) throws GeneralSecurityException {
        if (envelope.length < 46 || envelope.length > 8192 || envelope[0] != 1) throw new GeneralSecurityException("Invalid token envelope");
        return crypt(Cipher.DECRYPT_MODE, Arrays.copyOfRange(envelope,29,envelope.length), password, binding,
            Arrays.copyOfRange(envelope,1,17), Arrays.copyOfRange(envelope,17,29));
    }
    private static byte[] crypt(int mode, byte[] input, char[] password, String binding, byte[] salt, byte[] nonce) throws GeneralSecurityException {
        PBEKeySpec spec = new PBEKeySpec(password, salt, ITERATIONS, 256); byte[] key = null;
        try {
            key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(mode, new SecretKeySpec(key,"AES"), new GCMParameterSpec(128,nonce));
            cipher.updateAAD(binding.getBytes(StandardCharsets.UTF_8)); return cipher.doFinal(input);
        } finally { spec.clearPassword(); if(key != null) Arrays.fill(key,(byte)0); }
    }
}
