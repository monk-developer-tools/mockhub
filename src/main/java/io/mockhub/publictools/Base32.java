package io.mockhub.publictools;

import java.io.ByteArrayOutputStream;
import java.util.Locale;

final class Base32 {
  private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

  private Base32() {}

  static byte[] decode(String text) {
    String clean = text.replaceAll("[\\s-]", "").replaceAll("=+$", "").toUpperCase(Locale.ROOT);
    if (clean.isEmpty()) throw new IllegalArgumentException("The secret is empty");
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    int buffer = 0;
    int bits = 0;
    for (char c : clean.toCharArray()) {
      int value = ALPHABET.indexOf(c);
      if (value < 0) throw new IllegalArgumentException("The secret must be Base32: letters A to Z and digits 2 to 7");
      buffer = (buffer << 5) | value;
      bits += 5;
      if (bits >= 8) {
        bits -= 8;
        out.write((buffer >> bits) & 0xFF);
      }
    }
    return out.toByteArray();
  }
}
