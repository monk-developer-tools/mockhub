package io.mockhub.publictools;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

final class BinPathTemplate {
  private static final Pattern NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_]{0,39}");

  private BinPathTemplate() {}

  static void check(String template) {
    if (!template.startsWith("/")) throw new IllegalArgumentException("A path pattern starts with /, like /users/:id");
    for (String part : segments(template)) {
      String name = name(part);
      if (name != null && !NAME.matcher(name).matches()) {
        throw new IllegalArgumentException("Path parameter names use letters, digits and _, like :id or {orderId}");
      }
    }
  }

  static Optional<Map<String, String>> match(String template, String path) {
    String[] want = segments(template);
    String[] got = segments(path);
    if (want.length != got.length) return Optional.empty();
    Map<String, String> params = new LinkedHashMap<>();
    for (int i = 0; i < want.length; i++) {
      String name = name(want[i]);
      if (name == null) {
        if (!want[i].equals(got[i])) return Optional.empty();
      } else {
        if (got[i].isEmpty()) return Optional.empty();
        params.put(name, got[i]);
      }
    }
    return Optional.of(params);
  }

  private static String name(String segment) {
    if (segment.startsWith(":") && segment.length() > 1) return segment.substring(1);
    if (segment.startsWith("{") && segment.endsWith("}") && segment.length() > 2) return segment.substring(1, segment.length() - 1);
    return null;
  }

  private static String[] segments(String path) {
    String trimmed = path.length() > 1 && path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
    return trimmed.equals("/") ? new String[0] : trimmed.substring(1).split("/", -1);
  }
}
