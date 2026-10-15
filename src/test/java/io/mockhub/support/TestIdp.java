package io.mockhub.support;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.mockhub.api.Jsons;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;

public final class TestIdp {
  public static final String TENANT = "11111111-2222-3333-4444-555555555555";
  public static final String CLIENT = "client-123";
  public static final String KID = "test-key";

  private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
  private final KeyPair keys;

  public TestIdp() {
    try {
      KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
      g.initialize(2048);
      keys = g.generateKeyPair();
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  public ObjectNode jwks() {
    RSAPublicKey pub = (RSAPublicKey) keys.getPublic();
    ObjectNode out = Jsons.object();
    out.putArray("keys").addObject()
        .put("kty", "RSA").put("kid", KID).put("use", "sig")
        .put("n", B64.encodeToString(unsigned(pub.getModulus())))
        .put("e", B64.encodeToString(unsigned(pub.getPublicExponent())));
    return out;
  }

  public ObjectNode claims(String authority, String nonce, long nowSeconds) {
    ObjectNode c = Jsons.object();
    c.put("iss", authority + "/" + TENANT + "/v2.0");
    c.put("aud", CLIENT);
    c.put("tid", TENANT);
    c.put("oid", "user-oid-1");
    c.put("preferred_username", "ana@contoso.com");
    c.put("email", "ana@contoso.com");
    c.put("name", "Ana Tester");
    c.put("nonce", nonce);
    c.put("iat", nowSeconds);
    c.put("exp", nowSeconds + 3600);
    return c;
  }

  public String sign(ObjectNode claims) {
    return sign(claims, KID);
  }

  public String sign(ObjectNode claims, String kid) {
    String header = B64.encodeToString(Jsons.write(Jsons.object().put("alg", "RS256").put("kid", kid)).getBytes(StandardCharsets.UTF_8));
    String body = B64.encodeToString(Jsons.write(claims).getBytes(StandardCharsets.UTF_8));
    try {
      Signature s = Signature.getInstance("SHA256withRSA");
      s.initSign(keys.getPrivate());
      s.update((header + "." + body).getBytes(StandardCharsets.US_ASCII));
      return header + "." + body + "." + B64.encodeToString(s.sign());
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private static byte[] unsigned(BigInteger v) {
    byte[] b = v.toByteArray();
    if (b[0] == 0) {
      byte[] t = new byte[b.length - 1];
      System.arraycopy(b, 1, t, 0, t.length);
      return t;
    }
    return b;
  }
}
