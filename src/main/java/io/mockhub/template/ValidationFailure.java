package io.mockhub.template;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ValidationFailure extends RuntimeException {
  private static final Pattern MARKER = Pattern.compile("mockhub-invalid:(\\d{3}):([A-Za-z0-9_-]*)");

  public record Found(int status, String message) {}

  ValidationFailure(int status, String message) {
    super("mockhub-invalid:" + status + ":" + Base64.getUrlEncoder().withoutPadding().encodeToString(message.getBytes(StandardCharsets.UTF_8)));
  }

  public static Optional<Found> in(String text) {
    if (text == null) return Optional.empty();
    Matcher m = MARKER.matcher(text);
    if (!m.find()) return Optional.empty();
    return Optional.of(new Found(Integer.parseInt(m.group(1)), new String(Base64.getUrlDecoder().decode(m.group(2)), StandardCharsets.UTF_8)));
  }
}
