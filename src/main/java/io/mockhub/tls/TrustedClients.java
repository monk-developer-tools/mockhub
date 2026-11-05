package io.mockhub.tls;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.util.List;

public record TrustedClients(List<X509Certificate> authorities) {
  public static final int MAX_AUTHORITIES = 50;

  public TrustedClients {
    authorities = List.copyOf(authorities);
  }

  public static TrustedClients parse(String pem) {
    List<X509Certificate> authorities = Pem.certificates(pem);
    if (authorities.isEmpty()) throw new TlsException("Paste at least one CA certificate, -----BEGIN CERTIFICATE-----");
    if (authorities.size() > MAX_AUTHORITIES) throw new TlsException("Trust at most " + MAX_AUTHORITIES + " CA certificates");
    return new TrustedClients(authorities);
  }

  public KeyStore trustStore() {
    try {
      KeyStore store = KeyStore.getInstance("PKCS12");
      store.load(null, null);
      for (int i = 0; i < authorities.size(); i++) store.setCertificateEntry("ca-" + i, authorities.get(i));
      return store;
    } catch (GeneralSecurityException | IOException e) {
      throw new TlsException("The CA certificates can't be stored: " + e.getMessage());
    }
  }
}
