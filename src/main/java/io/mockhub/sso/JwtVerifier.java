package io.mockhub.sso;

import com.fasterxml.jackson.databind.JsonNode;
import io.mockhub.api.Jsons;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.RSAPublicKeySpec;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

public final class JwtVerifier {
  private static final Duration SKEW = Duration.ofMinutes(2);
  private static final Base64.Decoder B64 = Base64.getUrlDecoder();

  private final HttpTransport http;
  private final Clock clock;
  private final Map<String, Map<String, PublicKey>> keysByUrl = new ConcurrentHashMap<>();

  public JwtVerifier(HttpTransport http, Clock clock) {
    this.http = http;
    this.clock = clock;
  }

  public record Expectations(Predicate<String> issuer, String audience, String nonce) {}

  public JsonNode verify(String jwt, String jwksUrl, Expectations expected) {
    String[] parts = jwt == null ? new String[0] : jwt.split("\\.");
    if (parts.length != 3) throw new SsoException("The identity token is malformed");
    JsonNode header = decode(parts[0]);
    JsonNode claims = decode(parts[1]);
    if (!"RS256".equals(header.path("alg").asText())) {
      throw new SsoException("Unsupported token algorithm '" + header.path("alg").asText() + "'");
    }
    PublicKey key = key(jwksUrl, header.path("kid").asText());
    if (!signatureValid(key, parts[0] + "." + parts[1], B64.decode(parts[2]))) {
      throw new SsoException("The identity token signature is invalid");
    }
    check(claims, expected);
    return claims;
  }

  private void check(JsonNode claims, Expectations expected) {
    long now = clock.millis() / 1000;
    long skew = SKEW.toSeconds();
    if (!claims.hasNonNull("exp") || claims.get("exp").asLong() + skew < now) throw new SsoException("The identity token has expired");
    if (claims.hasNonNull("nbf") && claims.get("nbf").asLong() - skew > now) throw new SsoException("The identity token is not valid yet");
    if (!expected.issuer().test(claims.path("iss").asText())) {
      throw new SsoException("Unexpected token issuer '" + claims.path("iss").asText() + "'");
    }
    if (!audienceMatches(claims.path("aud"), expected.audience())) throw new SsoException("The identity token was issued for another application");
    if (expected.nonce() != null && !expected.nonce().equals(claims.path("nonce").asText())) {
      throw new SsoException("The sign-in response does not match this browser session");
    }
  }

  private static boolean audienceMatches(JsonNode aud, String expected) {
    if (aud.isArray()) {
      for (JsonNode a : aud) if (a.asText().equals(expected)) return true;
      return false;
    }
    return aud.asText().equals(expected);
  }

  private PublicKey key(String jwksUrl, String kid) {
    PublicKey cached = keysByUrl.getOrDefault(jwksUrl, Map.of()).get(kid);
    if (cached != null) return cached;
    Map<String, PublicKey> fresh = fetch(jwksUrl);
    keysByUrl.put(jwksUrl, fresh);
    PublicKey key = fresh.get(kid);
    if (key == null) throw new SsoException("No signing key '" + kid + "' at " + jwksUrl);
    return key;
  }

  private Map<String, PublicKey> fetch(String jwksUrl) {
    Map<String, PublicKey> keys = new HashMap<>();
    for (JsonNode jwk : http.getJson(jwksUrl).path("keys")) {
      if (!"RSA".equals(jwk.path("kty").asText())) continue;
      try {
        BigInteger n = new BigInteger(1, B64.decode(jwk.path("n").asText()));
        BigInteger e = new BigInteger(1, B64.decode(jwk.path("e").asText()));
        keys.put(jwk.path("kid").asText(), KeyFactory.getInstance("RSA").generatePublic(new RSAPublicKeySpec(n, e)));
      } catch (Exception ex) {
        throw new SsoException("Cannot read signing key " + jwk.path("kid").asText(), ex);
      }
    }
    return keys;
  }

  private static boolean signatureValid(PublicKey key, String signed, byte[] signature) {
    try {
      Signature verifier = Signature.getInstance("SHA256withRSA");
      verifier.initVerify(key);
      verifier.update(signed.getBytes(StandardCharsets.US_ASCII));
      return verifier.verify(signature);
    } catch (Exception e) {
      return false;
    }
  }

  private static JsonNode decode(String part) {
    try {
      return Jsons.MAPPER.readTree(B64.decode(part));
    } catch (Exception e) {
      throw new SsoException("The identity token is malformed", e);
    }
  }
}
