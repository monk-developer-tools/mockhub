package io.mockhub.tls;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.cert.X509Certificate;
import java.util.List;

public record ServerIdentity(List<X509Certificate> chain, PrivateKey key) {
  public static final String ALIAS = "mockhub";

  public ServerIdentity {
    chain = List.copyOf(chain);
  }

  public static ServerIdentity parse(String pem) {
    List<X509Certificate> chain = Pem.certificates(pem);
    PrivateKey key = Pem.privateKey(pem);
    if (chain.isEmpty()) throw new TlsException("Add the server certificate, -----BEGIN CERTIFICATE-----, before the key");
    if (key == null) throw new TlsException("Add the private key, -----BEGIN PRIVATE KEY-----, after the certificate");
    if (!matches(chain.get(0), key)) throw new TlsException("The private key doesn't belong to the first certificate. Put the server certificate first, then its chain");
    return new ServerIdentity(chain, key);
  }

  public X509Certificate leaf() {
    return chain.get(0);
  }

  public KeyStore keyStore(char[] password) {
    try {
      KeyStore store = KeyStore.getInstance("PKCS12");
      store.load(null, null);
      store.setKeyEntry(ALIAS, key, password, chain.toArray(X509Certificate[]::new));
      return store;
    } catch (GeneralSecurityException | IOException e) {
      throw new TlsException("The certificate and key can't be stored: " + e.getMessage());
    }
  }

  private static boolean matches(X509Certificate certificate, PrivateKey key) {
    String algorithm = switch (key.getAlgorithm()) {
      case "RSA" -> "SHA256withRSA";
      case "EC" -> "SHA256withECDSA";
      default -> key.getAlgorithm();
    };
    try {
      byte[] probe = new byte[32];
      new SecureRandom().nextBytes(probe);
      Signature signer = Signature.getInstance(algorithm);
      signer.initSign(key);
      signer.update(probe);
      byte[] signature = signer.sign();
      Signature verifier = Signature.getInstance(algorithm);
      verifier.initVerify(certificate.getPublicKey());
      verifier.update(probe);
      return verifier.verify(signature);
    } catch (GeneralSecurityException e) {
      return false;
    }
  }
}
