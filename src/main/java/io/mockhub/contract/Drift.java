package io.mockhub.contract;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public record Drift(DriftKind kind, String method, String path, String detail) {

  public String key() {
    try {
      byte[] hash = MessageDigest.getInstance("SHA-256")
          .digest((kind + "\n" + method + "\n" + path + "\n" + detail).getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(hash, 0, 8);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
