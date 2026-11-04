package io.mockhub.template;

import com.fasterxml.jackson.core.type.TypeReference;
import com.github.jknack.handlebars.Helper;
import com.github.jknack.handlebars.Options;
import com.github.tomakehurst.wiremock.extension.TemplateHelperProviderExtension;
import io.mockhub.api.Jsons;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.Signature;
import java.time.Clock;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class JwtHelpers implements TemplateHelperProviderExtension {
  private static final Base64.Encoder URL = Base64.getUrlEncoder().withoutPadding();
  private static final Set<String> OPTIONS = Set.of("alg", "secret", "expiresIn", "claims");
  private static final long DEFAULT_LIFETIME = 3600;

  private final JwtKeys keys;
  private final Clock clock;

  public JwtHelpers(JwtKeys keys, Clock clock) {
    this.keys = keys;
    this.clock = clock;
  }

  @Override
  public String getName() {
    return "mockhub-jwt";
  }

  @Override
  public Map<String, Helper<?>> provideTemplateHelpers() {
    return Map.of(
        "jwt", (Helper<Object>) (context, options) -> sign(options),
        "jwtDecode", (Helper<Object>) this::decode,
        "jwks", (Helper<Object>) (context, options) -> JsonValue.stringify(keys.jwks(), false));
  }

  private String sign(Options options) {
    Object algOption = options.hash("alg", "RS256");
    String alg = algOption.toString().toUpperCase();
    if (!alg.equals("RS256") && !alg.equals("HS256")) throw new IllegalArgumentException("jwt supports alg='RS256' or alg='HS256'");
    long now = clock.instant().getEpochSecond();
    Map<String, Object> claims = new LinkedHashMap<>();
    claims.put("iat", now);
    Object lifetime = ValueHelpers.number(options.hash("expiresIn", DEFAULT_LIFETIME), DEFAULT_LIFETIME);
    claims.put("exp", now + ((Number) lifetime).longValue());
    if (options.hash("claims") instanceof Map<?, ?> extra) extra.forEach((k, v) -> claims.put(String.valueOf(k), v));
    options.hash.forEach((k, v) -> {
      if (!OPTIONS.contains(k)) claims.put(k, v);
    });
    Map<String, Object> header = new LinkedHashMap<>();
    header.put("alg", alg);
    header.put("typ", "JWT");
    if (alg.equals("RS256")) header.put("kid", keys.kid());
    String unsigned = part(header) + "." + part(claims);
    return unsigned + "." + URL.encodeToString(signature(alg, unsigned, options.hash("secret")));
  }

  private byte[] signature(String alg, String unsigned, Object secret) {
    byte[] data = unsigned.getBytes(StandardCharsets.US_ASCII);
    try {
      if (alg.equals("HS256")) {
        if (ValueHelpers.empty(secret)) throw new IllegalArgumentException("jwt with alg='HS256' needs secret='…'");
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.toString().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return mac.doFinal(data);
      }
      Signature rsa = Signature.getInstance("SHA256withRSA");
      rsa.initSign(keys.privateKey());
      rsa.update(data);
      return rsa.sign();
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("Can't sign the JWT", e);
    }
  }

  private Object decode(Object token, Options options) {
    String text = token == null ? "" : token.toString().trim();
    if (text.regionMatches(true, 0, "Bearer ", 0, 7)) text = text.substring(7).trim();
    String[] parts = text.split("\\.");
    if (parts.length < 2) return options.params.length > 0 ? null : new JsonValue(Map.of());
    try {
      Map<String, Object> claims = Jsons.MAPPER.readValue(Base64.getUrlDecoder().decode(parts[1]), new TypeReference<LinkedHashMap<String, Object>>() {});
      return options.params.length > 0 ? claims.get(param(options)) : new JsonValue(claims);
    } catch (Exception e) {
      return options.params.length > 0 ? null : new JsonValue(Map.of());
    }
  }

  private static String param(Options options) {
    Object value = options.param(0);
    return String.valueOf(value);
  }

  private static String part(Map<String, Object> value) {
    return URL.encodeToString(JsonValue.stringify(value, false).getBytes(StandardCharsets.UTF_8));
  }
}
