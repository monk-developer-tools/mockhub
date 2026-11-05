package io.mockhub.project;

import com.fasterxml.jackson.databind.JsonNode;
import io.mockhub.api.ApiException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

public final class Domains {
  public static final int MAX_PER_PROJECT = 10;
  private static final Pattern LABEL = Pattern.compile("[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?");
  private static final Pattern ALL_DIGITS = Pattern.compile("[0-9.]+");

  private Domains() {}

  public static List<String> parse(JsonNode node) {
    if (node == null || node.isNull()) return List.of();
    if (!node.isArray()) throw ApiException.badRequest("domains must be a list of host names");
    Set<String> out = new LinkedHashSet<>();
    for (JsonNode item : node) {
      String text = item.asText("").trim();
      if (!text.isEmpty()) out.add(normalize(text));
    }
    if (out.size() > MAX_PER_PROJECT) throw ApiException.badRequest("A project can have at most " + MAX_PER_PROJECT + " domains");
    return new ArrayList<>(out);
  }

  public static String normalize(String domain) {
    String host = domain.trim().toLowerCase(Locale.ROOT);
    if (host.endsWith(".")) host = host.substring(0, host.length() - 1);
    if (host.contains("://") || host.contains("/") || host.contains(":") || host.contains("*")) {
      throw ApiException.badRequest("Enter only a host name, like payments.mocks.example.com, without scheme, port, path or wildcard: " + domain);
    }
    if (host.length() > 253 || ALL_DIGITS.matcher(host).matches() || !host.contains(".") && !host.equals("localhost")) {
      throw ApiException.badRequest("Not a host name: " + domain);
    }
    for (String label : host.split("\\.", -1)) {
      if (!LABEL.matcher(label).matches()) throw ApiException.badRequest("Not a host name: " + domain);
    }
    return host;
  }

  public static Optional<String> hostOf(String hostHeader) {
    if (hostHeader == null || hostHeader.isBlank()) return Optional.empty();
    String host = hostHeader.trim().toLowerCase(Locale.ROOT);
    if (host.startsWith("[")) return Optional.empty();
    int colon = host.indexOf(':');
    if (colon >= 0) host = host.substring(0, colon);
    if (host.endsWith(".")) host = host.substring(0, host.length() - 1);
    return host.isEmpty() ? Optional.empty() : Optional.of(host);
  }
}
