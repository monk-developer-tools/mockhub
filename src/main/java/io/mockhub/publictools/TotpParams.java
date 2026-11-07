package io.mockhub.publictools;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

record TotpParams(byte[] key, int digits, int period, String algorithm, String issuer, String account) {
  static final int MAX_SECRET = 256;
  static final Set<String> ALGORITHMS = Set.of("SHA1", "SHA256", "SHA512");

  record Input(String secret, String uri, String digits, String period, String algorithm) {}

  static TotpParams of(Input input) {
    String uri = blank(input.uri()) ? (startsWithScheme(input.secret()) ? input.secret().trim() : "") : input.uri().trim();
    Map<String, String> query = uri.isEmpty() ? Map.of() : query(uri);
    String secret = uri.isEmpty() ? input.secret() : query.get("secret");
    if (blank(secret)) throw new IllegalArgumentException(uri.isEmpty() ? "Send a Base32 secret or an otpauth:// link" : "The otpauth:// link has no secret");
    if (secret.length() > MAX_SECRET) throw new IllegalArgumentException("Keep the secret under " + MAX_SECRET + " characters");
    String[] label = uri.isEmpty() ? new String[] {"", ""} : label(uri);
    String issuer = query.getOrDefault("issuer", label[0]);
    int digits = number(first(input.digits(), query.get("digits")), 6, "digits");
    int period = number(first(input.period(), query.get("period")), 30, "period");
    String algorithm = first(input.algorithm(), query.get("algorithm"));
    algorithm = blank(algorithm) ? "SHA1" : algorithm.trim().toUpperCase(Locale.ROOT).replace("-", "");
    if (digits < 6 || digits > 8) throw new IllegalArgumentException("digits must be 6, 7 or 8");
    if (period < 10 || period > 300) throw new IllegalArgumentException("period must be between 10 and 300 seconds");
    if (!ALGORITHMS.contains(algorithm)) throw new IllegalArgumentException("algorithm must be SHA1, SHA256 or SHA512");
    return new TotpParams(Base32.decode(secret), digits, period, algorithm, issuer, label[1]);
  }

  private static boolean startsWithScheme(String value) {
    return value != null && value.trim().regionMatches(true, 0, "otpauth://", 0, 10);
  }

  private static Map<String, String> query(String uri) {
    URI parsed;
    try {
      parsed = new URI(uri);
    } catch (URISyntaxException e) {
      throw new IllegalArgumentException("That otpauth:// link isn't a valid URL");
    }
    if (!"otpauth".equalsIgnoreCase(parsed.getScheme())) throw new IllegalArgumentException("The link must start with otpauth://");
    if ("hotp".equalsIgnoreCase(parsed.getHost())) throw new IllegalArgumentException("Only time-based codes (otpauth://totp/…) are supported, not counter-based HOTP");
    if (!"totp".equalsIgnoreCase(parsed.getHost())) throw new IllegalArgumentException("The link must start with otpauth://totp/");
    Map<String, String> out = new LinkedHashMap<>();
    String raw = parsed.getRawQuery();
    if (raw == null) return out;
    for (String pair : raw.split("&")) {
      int eq = pair.indexOf('=');
      if (eq <= 0) continue;
      out.putIfAbsent(decode(pair.substring(0, eq)).toLowerCase(Locale.ROOT), decode(pair.substring(eq + 1)));
    }
    return out;
  }

  private static String[] label(String uri) {
    String path = URI.create(uri).getRawPath();
    String text = path == null ? "" : decode(path.replaceFirst("^/", ""));
    int colon = text.indexOf(':');
    return colon < 0 ? new String[] {"", text.trim()} : new String[] {text.substring(0, colon).trim(), text.substring(colon + 1).trim()};
  }

  private static String decode(String value) {
    return URLDecoder.decode(value.replace("+", "%2B"), StandardCharsets.UTF_8);
  }

  private static int number(String value, int fallback, String name) {
    if (blank(value)) return fallback;
    try {
      return Integer.parseInt(value.trim());
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException(name + " must be a whole number");
    }
  }

  private static String first(String a, String b) {
    return blank(a) ? b : a;
  }

  private static boolean blank(String value) {
    return value == null || value.isBlank();
  }
}
