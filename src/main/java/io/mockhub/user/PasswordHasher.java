package io.mockhub.user;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public final class PasswordHasher {
  private static final String PREFIX = "pbkdf2";
  private static final int ITERATIONS = 210_000;
  private static final int KEY_BITS = 256;
  private static final SecureRandom RANDOM = new SecureRandom();

  private final int iterations;

  public PasswordHasher() {
    this(ITERATIONS);
  }

  public PasswordHasher(int iterations) {
    this.iterations = iterations;
  }

  public String hash(String password) {
    byte[] salt = new byte[16];
    RANDOM.nextBytes(salt);
    byte[] key = derive(password, salt, iterations);
    Base64.Encoder b64 = Base64.getEncoder().withoutPadding();
    return PREFIX + ":" + iterations + ":" + b64.encodeToString(salt) + ":" + b64.encodeToString(key);
  }

  public boolean matches(String stored, String supplied) {
    if (stored == null || supplied == null) return false;
    if (stored.startsWith(PREFIX + ":")) {
      String[] parts = stored.split(":");
      if (parts.length != 4) return false;
      byte[] salt = Base64.getDecoder().decode(parts[2]);
      byte[] expected = Base64.getDecoder().decode(parts[3]);
      return MessageDigest.isEqual(expected, derive(supplied, salt, Integer.parseInt(parts[1])));
    }
    if (stored.startsWith("sha256:")) {
      return MessageDigest.isEqual(
          stored.substring(7).toLowerCase().getBytes(StandardCharsets.UTF_8),
          sha256Hex(supplied).getBytes(StandardCharsets.UTF_8));
    }
    return MessageDigest.isEqual(stored.getBytes(StandardCharsets.UTF_8), supplied.getBytes(StandardCharsets.UTF_8));
  }

  private static byte[] derive(String password, byte[] salt, int iterations) {
    try {
      PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, KEY_BITS);
      return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private static String sha256Hex(String s) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }
}
