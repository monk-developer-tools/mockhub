package io.mockhub.publictools;

import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

final class Totp {
  private static final int[] POWERS = {1, 10, 100, 1_000, 10_000, 100_000, 1_000_000, 10_000_000, 100_000_000};

  private Totp() {}

  static String code(TotpParams params, long epochSeconds) {
    return hotp(params, Math.floorDiv(epochSeconds, params.period()));
  }

  private static String hotp(TotpParams params, long counter) {
    byte[] hash = hmac(params.algorithm(), params.key(), ByteBuffer.allocate(8).putLong(counter).array());
    int offset = hash[hash.length - 1] & 0x0F;
    int binary = ((hash[offset] & 0x7F) << 24) | ((hash[offset + 1] & 0xFF) << 16) | ((hash[offset + 2] & 0xFF) << 8) | (hash[offset + 3] & 0xFF);
    String code = Integer.toString(binary % POWERS[params.digits()]);
    return "0".repeat(params.digits() - code.length()) + code;
  }

  private static byte[] hmac(String algorithm, byte[] key, byte[] message) {
    String name = "Hmac" + algorithm;
    try {
      Mac mac = Mac.getInstance(name);
      mac.init(new SecretKeySpec(key, name));
      return mac.doFinal(message);
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("HMAC " + algorithm + " is not available", e);
    }
  }
}
