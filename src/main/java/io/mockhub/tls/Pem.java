package io.mockhub.tls;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Pem {
  public static final int MAX_CHARS = 256 * 1024;
  private static final Pattern BLOCK = Pattern.compile("-----BEGIN ([A-Z0-9 ]+)-----([A-Za-z0-9+/=\\s]*)-----END \\1-----");
  private static final byte[] RSA_ALGORITHM = {0x30, 0x0d, 0x06, 0x09, 0x2a, (byte) 0x86, 0x48, (byte) 0x86, (byte) 0xf7, 0x0d, 0x01, 0x01, 0x01, 0x05, 0x00};
  private static final String[] KEY_ALGORITHMS = {"RSA", "EC", "Ed25519", "Ed448"};

  private Pem() {}

  public static List<X509Certificate> certificates(String pem) {
    List<X509Certificate> out = new ArrayList<>();
    for (Block block : blocks(pem)) {
      if (!block.type().equals("CERTIFICATE")) continue;
      try {
        out.add((X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(new ByteArrayInputStream(block.der())));
      } catch (CertificateException e) {
        throw new TlsException("A certificate in the PEM text can't be read");
      }
    }
    return out;
  }

  public static PrivateKey privateKey(String pem) {
    PrivateKey found = null;
    for (Block block : blocks(pem)) {
      PrivateKey key = switch (block.type()) {
        case "PRIVATE KEY" -> pkcs8(block.der());
        case "RSA PRIVATE KEY" -> pkcs8(wrapRsa(block.der()));
        case "ENCRYPTED PRIVATE KEY" -> throw new TlsException("The private key is encrypted. Remove the password first: openssl pkcs8 -topk8 -nocrypt -in key.pem");
        case "EC PRIVATE KEY" -> throw new TlsException("Convert the EC key to PKCS#8 first: openssl pkcs8 -topk8 -nocrypt -in key.pem");
        default -> null;
      };
      if (key == null) continue;
      if (found != null) throw new TlsException("The PEM text holds more than one private key");
      found = key;
    }
    return found;
  }

  private static List<Block> blocks(String pem) {
    if (pem == null || pem.isBlank()) throw new TlsException("Paste the PEM text");
    if (pem.length() > MAX_CHARS) throw new TlsException("The PEM text can be at most 256 KB");
    List<Block> out = new ArrayList<>();
    Matcher m = BLOCK.matcher(pem);
    while (m.find()) {
      try {
        out.add(new Block(m.group(1), Base64.getMimeDecoder().decode(m.group(2))));
      } catch (IllegalArgumentException e) {
        throw new TlsException("A " + m.group(1).toLowerCase() + " block is not valid base64");
      }
    }
    if (out.isEmpty()) throw new TlsException("No PEM blocks found. They start with -----BEGIN CERTIFICATE----- or -----BEGIN PRIVATE KEY-----");
    return out;
  }

  private static PrivateKey pkcs8(byte[] der) {
    for (String algorithm : KEY_ALGORITHMS) {
      try {
        return KeyFactory.getInstance(algorithm).generatePrivate(new PKCS8EncodedKeySpec(der));
      } catch (GeneralSecurityException | IllegalArgumentException e) {
        continue;
      }
    }
    throw new TlsException("The private key can't be read. MockHub reads RSA, EC and EdDSA keys in PKCS#8 or RSA keys in PKCS#1");
  }

  private static byte[] wrapRsa(byte[] pkcs1) {
    ByteArrayOutputStream body = new ByteArrayOutputStream();
    body.writeBytes(new byte[] {0x02, 0x01, 0x00});
    body.writeBytes(RSA_ALGORITHM);
    body.writeBytes(tlv(0x04, pkcs1));
    return tlv(0x30, body.toByteArray());
  }

  private static byte[] tlv(int tag, byte[] value) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    out.write(tag);
    int length = value.length;
    if (length < 0x80) {
      out.write(length);
    } else {
      int bytes = (Integer.SIZE - Integer.numberOfLeadingZeros(length) + 7) / 8;
      out.write(0x80 | bytes);
      for (int i = bytes - 1; i >= 0; i--) out.write(length >>> (8 * i));
    }
    out.writeBytes(value);
    return out.toByteArray();
  }

  private record Block(String type, byte[] der) {}
}
