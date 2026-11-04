package io.mockhub.connection;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public final class SecretBox {
  private static final String PREFIX = "v1:";
  private static final int IV_BYTES = 12;
  private static final int TAG_BITS = 128;
  private static final SecureRandom RANDOM = new SecureRandom();

  private final SecretKeySpec key;

  public SecretBox(byte[] keyBytes) {
    if (keyBytes.length != 32) throw new IllegalArgumentException("The secret key must be 32 bytes");
    this.key = new SecretKeySpec(keyBytes.clone(), "AES");
  }

  public static SecretBox of(String configured, Path keyFile) {
    if (configured != null && !configured.isBlank()) return new SecretBox(sha256(configured.trim()));
    return new SecretBox(keyFrom(keyFile));
  }

  public String seal(String plain) {
    if (plain == null || plain.isEmpty()) return "";
    try {
      byte[] iv = new byte[IV_BYTES];
      RANDOM.nextBytes(iv);
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
      byte[] sealed = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
      byte[] out = new byte[iv.length + sealed.length];
      System.arraycopy(iv, 0, out, 0, iv.length);
      System.arraycopy(sealed, 0, out, iv.length, sealed.length);
      return PREFIX + Base64.getEncoder().encodeToString(out);
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("Can't encrypt the secret", e);
    }
  }

  public String open(String sealed) {
    if (sealed == null || sealed.isEmpty()) return "";
    if (!sealed.startsWith(PREFIX)) throw new SecretException("The stored secret has an unknown format; enter it again");
    try {
      byte[] all = Base64.getDecoder().decode(sealed.substring(PREFIX.length()));
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, all, 0, IV_BYTES));
      return new String(cipher.doFinal(all, IV_BYTES, all.length - IV_BYTES), StandardCharsets.UTF_8);
    } catch (GeneralSecurityException | IllegalArgumentException e) {
      throw new SecretException("The stored secret can't be read with this server's key; enter it again");
    }
  }

  private static byte[] sha256(String text) {
    try {
      return MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException(e);
    }
  }

  private static synchronized byte[] keyFrom(Path file) {
    try {
      if (Files.exists(file)) return Base64.getDecoder().decode(Files.readString(file).trim());
      Files.createDirectories(file.getParent());
      byte[] key = new byte[32];
      RANDOM.nextBytes(key);
      Files.writeString(file, Base64.getEncoder().encodeToString(key));
      try {
        Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-------"));
      } catch (UnsupportedOperationException e) {
        file.toFile().setReadable(true, true);
      }
      return key;
    } catch (IOException e) {
      throw new UncheckedIOException("Can't read or create the secret key " + file, e);
    }
  }

  public static final class SecretException extends RuntimeException {
    public SecretException(String message) {
      super(message);
    }
  }
}
