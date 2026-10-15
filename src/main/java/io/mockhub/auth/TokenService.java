package io.mockhub.auth;

import com.fasterxml.jackson.databind.JsonNode;
import io.mockhub.api.Jsons;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class TokenService {
  public static final Duration DEFAULT_LIFETIME = Duration.ofHours(12);

  private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
  private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

  private final byte[] secret;
  private final Clock clock;
  private final Duration lifetime;

  public TokenService(byte[] secret, Clock clock, Duration lifetime) {
    this.secret = secret.clone();
    this.clock = clock;
    this.lifetime = lifetime;
  }

  public static TokenService fromDataDir(Path rootDir, Clock clock) {
    return new TokenService(loadOrCreateSecret(rootDir.resolve("mockhub").resolve("secret.key")), clock, DEFAULT_LIFETIME);
  }

  public record Claims(String subject, long issuedAt, long expiresAt) {}

  public String issue(String subject) {
    long now = clock.millis();
    String payload = Jsons.write(Jsons.object().put("sub", subject).put("iat", now).put("exp", now + lifetime.toMillis()));
    String body = ENCODER.encodeToString(payload.getBytes(StandardCharsets.UTF_8));
    return body + "." + ENCODER.encodeToString(sign(body));
  }

  public Optional<Claims> verify(String token) {
    if (token == null) return Optional.empty();
    int dot = token.indexOf('.');
    if (dot <= 0 || dot != token.lastIndexOf('.')) return Optional.empty();
    String body = token.substring(0, dot);
    try {
      if (!MessageDigest.isEqual(sign(body), DECODER.decode(token.substring(dot + 1)))) return Optional.empty();
      JsonNode claims = Jsons.MAPPER.readTree(DECODER.decode(body));
      Claims result = new Claims(claims.path("sub").asText(), claims.path("iat").asLong(), claims.path("exp").asLong());
      return result.expiresAt() > clock.millis() && !result.subject().isEmpty() ? Optional.of(result) : Optional.empty();
    } catch (IllegalArgumentException | IOException e) {
      return Optional.empty();
    }
  }

  public long lifetimeSeconds() {
    return lifetime.toSeconds();
  }

  private byte[] sign(String body) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret, "HmacSHA256"));
      return mac.doFinal(body.getBytes(StandardCharsets.UTF_8));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  static byte[] loadOrCreateSecret(Path file) {
    try {
      if (Files.exists(file)) return Base64.getDecoder().decode(Files.readString(file).trim());
      byte[] secret = new byte[32];
      new SecureRandom().nextBytes(secret);
      Files.createDirectories(file.getParent());
      Files.writeString(file, Base64.getEncoder().encodeToString(secret));
      try {
        Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-------"));
      } catch (UnsupportedOperationException ignored) {
        return secret;
      }
      return secret;
    } catch (IOException e) {
      throw new UncheckedIOException("Cannot read or create " + file, e);
    }
  }
}
