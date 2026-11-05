package io.mockhub.tls;

import java.security.KeyStore;

public interface TlsTarget {
  boolean enabled();

  int httpsPort();

  void apply(KeyStore identity, char[] password, KeyStore trusted);
}
