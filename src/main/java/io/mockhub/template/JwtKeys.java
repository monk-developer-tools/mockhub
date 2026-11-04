package io.mockhub.template;

import com.fasterxml.jackson.core.type.TypeReference;
import io.mockhub.store.JsonFile;
import java.math.BigInteger;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class JwtKeys {
  private static final Base64.Encoder URL = Base64.getUrlEncoder().withoutPadding();

  private record Stored(String kid, String privateKey, String publicKey) {}

  private final JsonFile<Stored> file;
  private volatile Loaded loaded;

  private record Loaded(String kid, RSAPrivateKey privateKey, RSAPublicKey publicKey) {}

  public JwtKeys(Path dataDir) {
    this.file = new JsonFile<>(JsonFile.inDataDir(dataDir, "jwt-key.json"), new TypeReference<>() {});
  }

  public String kid() {
    return load().kid();
  }

  public RSAPrivateKey privateKey() {
    return load().privateKey();
  }

  public RSAPublicKey publicKey() {
    return load().publicKey();
  }

  public Map<String, Object> jwks() {
    RSAPublicKey key = publicKey();
    Map<String, Object> jwk = new LinkedHashMap<>();
    jwk.put("kty", "RSA");
    jwk.put("use", "sig");
    jwk.put("alg", "RS256");
    jwk.put("kid", kid());
    jwk.put("n", URL.encodeToString(unsigned(key.getModulus())));
    jwk.put("e", URL.encodeToString(unsigned(key.getPublicExponent())));
    return Map.of("keys", List.of(jwk));
  }

  private Loaded load() {
    Loaded current = loaded;
    if (current != null) return current;
    synchronized (this) {
      if (loaded == null) loaded = decode(file.read(this::create));
      return loaded;
    }
  }

  private Stored create() {
    try {
      KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
      generator.initialize(2048);
      KeyPair pair = generator.generateKeyPair();
      byte[] publicKey = pair.getPublic().getEncoded();
      String kid = URL.encodeToString(MessageDigest.getInstance("SHA-256").digest(publicKey)).substring(0, 16);
      Stored stored = new Stored(kid, Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded()), Base64.getEncoder().encodeToString(publicKey));
      file.write(stored);
      return stored;
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("Can't create the JWT signing key", e);
    }
  }

  private static Loaded decode(Stored stored) {
    try {
      KeyFactory rsa = KeyFactory.getInstance("RSA");
      return new Loaded(stored.kid(),
          (RSAPrivateKey) rsa.generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(stored.privateKey()))),
          (RSAPublicKey) rsa.generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(stored.publicKey()))));
    } catch (GeneralSecurityException | IllegalArgumentException e) {
      throw new IllegalStateException("The JWT signing key in jwt-key.json can't be read", e);
    }
  }

  private static byte[] unsigned(BigInteger value) {
    byte[] bytes = value.toByteArray();
    if (bytes.length > 1 && bytes[0] == 0) {
      byte[] trimmed = new byte[bytes.length - 1];
      System.arraycopy(bytes, 1, trimmed, 0, trimmed.length);
      return trimmed;
    }
    return bytes;
  }
}
