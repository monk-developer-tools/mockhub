package io.mockhub.tls;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

public final class TlsFixtures {
  public static final String CA = read("ca.pem");
  public static final String SERVER = read("server.pem");
  public static final String SERVER_PKCS1 = read("server-pkcs1.pem");
  public static final String CLIENT = read("client.pem");
  public static final String ROGUE = read("rogue.pem");

  private TlsFixtures() {}

  public static String certificatesOnly(String pem) {
    return pem.substring(0, pem.indexOf("-----END CERTIFICATE-----") + "-----END CERTIFICATE-----".length()) + "\n";
  }

  public static String keyOnly(String pem) {
    return pem.substring(pem.indexOf("-----BEGIN", pem.indexOf("-----END CERTIFICATE-----")));
  }

  private static String read(String name) {
    try (InputStream in = TlsFixtures.class.getResourceAsStream("/tls/" + name)) {
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
