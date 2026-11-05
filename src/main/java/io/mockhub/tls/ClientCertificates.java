package io.mockhub.tls;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class ClientCertificates {
  public static final String PREFIX = "x-mockhub-client-cert";
  public static final String SUBJECT = "X-MockHub-Client-Cert-Subject";
  public static final String ISSUER = "X-MockHub-Client-Cert-Issuer";
  public static final String FINGERPRINT = "X-MockHub-Client-Cert-Sha256";

  private ClientCertificates() {}

  public static boolean isReserved(String header) {
    return header != null && header.toLowerCase(Locale.ROOT).startsWith(PREFIX);
  }

  public static Map<String, String> headers(X509Certificate certificate) {
    Map<String, String> out = new LinkedHashMap<>();
    out.put(SUBJECT, certificate.getSubjectX500Principal().getName());
    out.put(ISSUER, certificate.getIssuerX500Principal().getName());
    out.put(FINGERPRINT, fingerprint(certificate));
    return out;
  }

  static String fingerprint(X509Certificate certificate) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(certificate.getEncoded()));
    } catch (NoSuchAlgorithmException | CertificateEncodingException e) {
      throw new IllegalStateException(e);
    }
  }
}
