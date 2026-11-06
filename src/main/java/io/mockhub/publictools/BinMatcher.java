package io.mockhub.publictools;

import com.fasterxml.jackson.databind.JsonNode;
import io.mockhub.api.Jsons;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

final class BinMatcher {
  static final int MAX_REGEX_STEPS = 200_000;

  private BinMatcher() {}

  record Call(String method, String path, PublicRequest request) {
    JsonNode json() {
      try {
        String body = request.body();
        return body == null || body.isBlank() ? null : Jsons.MAPPER.readTree(body);
      } catch (Exception e) {
        return null;
      }
    }
  }

  static Optional<BinRule> first(List<BinRule> rules, Call call) {
    JsonNode[] json = new JsonNode[1];
    boolean[] parsed = new boolean[1];
    for (BinRule rule : rules) {
      if (!rule.enabled()) continue;
      if (!rule.method().equals("ANY") && !rule.method().equals(call.method())) continue;
      boolean all = true;
      for (BinRule.Condition c : rule.conditions()) {
        if (c.target() == BinRule.Target.JSON && !parsed[0]) {
          json[0] = call.json();
          parsed[0] = true;
        }
        if (!holds(c, call, json[0])) {
          all = false;
          break;
        }
      }
      if (all) return Optional.of(rule);
    }
    return Optional.empty();
  }

  static boolean holds(BinRule.Condition c, Call call, JsonNode json) {
    Optional<String> actual = switch (c.target()) {
      case PATH -> Optional.of(call.path());
      case QUERY -> Optional.ofNullable(call.request().firstQuery(c.key()));
      case HEADER -> Optional.ofNullable(call.request().header(c.key()));
      case BODY -> Optional.ofNullable(call.request().body()).filter(b -> !b.isEmpty());
      case JSON -> read(json, c.key());
    };
    if (c.op() == BinRule.Op.EXISTS) return actual.isPresent();
    if (actual.isEmpty()) return false;
    String value = actual.get();
    return switch (c.op()) {
      case EQUALS -> value.equals(c.value());
      case STARTS_WITH -> value.startsWith(c.value());
      case CONTAINS -> value.contains(c.value());
      case MATCHES -> matches(c.value(), value);
      case EXISTS -> true;
    };
  }

  static boolean matches(String regex, String value) {
    try {
      return Pattern.compile(regex).matcher(new Bounded(value, MAX_REGEX_STEPS)).find();
    } catch (Bounded.TooSlow e) {
      return false;
    }
  }

  static Optional<String> read(JsonNode root, String path) {
    if (root == null || !path.startsWith("$")) return Optional.empty();
    JsonNode node = root;
    String rest = path.substring(1);
    int i = 0;
    while (i < rest.length() && node != null) {
      char ch = rest.charAt(i);
      if (ch == '.') {
        int end = i + 1;
        while (end < rest.length() && rest.charAt(end) != '.' && rest.charAt(end) != '[') end++;
        String field = rest.substring(i + 1, end);
        if (field.isEmpty()) return Optional.empty();
        node = node.isObject() ? node.get(field) : null;
        i = end;
      } else if (ch == '[') {
        int end = rest.indexOf(']', i);
        if (end < 0) return Optional.empty();
        String inside = rest.substring(i + 1, end).trim();
        if (inside.startsWith("'") && inside.endsWith("'") && inside.length() >= 2) {
          node = node.isObject() ? node.get(inside.substring(1, inside.length() - 1)) : null;
        } else {
          try {
            int index = Integer.parseInt(inside);
            node = node.isArray() ? node.get(index < 0 ? node.size() + index : index) : null;
          } catch (NumberFormatException e) {
            return Optional.empty();
          }
        }
        i = end + 1;
      } else {
        return Optional.empty();
      }
    }
    if (node == null || node.isMissingNode() || node.isNull()) return Optional.empty();
    return Optional.of(node.isValueNode() ? node.asText() : Jsons.write(node));
  }

  static String eventOf(PublicRequest request, JsonNode json) {
    for (String header : List.of("X-GitHub-Event", "X-Gitlab-Event", "X-Event-Key", "X-Shopify-Topic", "X-Webhook-Event", "X-Event-Type")) {
      String value = request.header(header);
      if (value != null && !value.isBlank()) return value.trim();
    }
    if (json == null || !json.isObject()) return "";
    for (String field : List.of("type", "event", "event_type", "eventType", "topic", "action")) {
      JsonNode v = json.get(field);
      if (v != null && v.isTextual() && !v.asText().isBlank() && v.asText().length() <= 80) return v.asText().trim();
    }
    return "";
  }

  static final class Bounded implements CharSequence {
    static final class TooSlow extends RuntimeException {
      TooSlow() {
        super(null, null, false, false);
      }
    }

    private final String text;
    private final int[] budget;

    Bounded(String text, int steps) {
      this(text, new int[] {steps});
    }

    private Bounded(String text, int[] budget) {
      this.text = text;
      this.budget = budget;
    }

    @Override
    public char charAt(int index) {
      if (--budget[0] < 0) throw new TooSlow();
      return text.charAt(index);
    }

    @Override
    public int length() {
      return text.length();
    }

    @Override
    public CharSequence subSequence(int start, int end) {
      return new Bounded(text.substring(start, end), budget);
    }

    @Override
    public String toString() {
      return text;
    }
  }
}
