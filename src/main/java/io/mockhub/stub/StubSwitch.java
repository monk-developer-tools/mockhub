package io.mockhub.stub;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.tomakehurst.wiremock.stubbing.StubMapping;

public final class StubSwitch {
  public static final String MATCHER = "mockhub-disabled";
  public static final String DISABLED = "disabled";
  private static final String ORIGINAL = "original";

  private StubSwitch() {}

  public static boolean isDisabled(StubMapping stub) {
    return Boolean.TRUE.equals(StubMetadata.of(stub).get(DISABLED));
  }

  public static void apply(ObjectNode stub) {
    ObjectNode meta = StubMetadata.node(stub);
    boolean disabled = meta.path(DISABLED).asBoolean(false);
    ObjectNode request = stub.get("request") instanceof ObjectNode r ? r : stub.putObject("request");
    JsonNode original = original(request.get("customMatcher"));
    if (disabled) {
      ObjectNode matcher = request.putObject("customMatcher");
      matcher.put("name", MATCHER);
      if (original != null) matcher.putObject("parameters").set(ORIGINAL, original);
      meta.put(DISABLED, true);
    } else {
      meta.remove(DISABLED);
      if (original == null) request.remove("customMatcher");
      else request.set("customMatcher", original);
    }
  }

  public static void reveal(ObjectNode stub) {
    if (!(stub.get("request") instanceof ObjectNode request)) return;
    JsonNode current = request.get("customMatcher");
    if (!isOurs(current)) return;
    JsonNode original = original(current);
    if (original == null) request.remove("customMatcher");
    else request.set("customMatcher", original);
  }

  private static JsonNode original(JsonNode matcher) {
    if (matcher == null || matcher.isNull()) return null;
    if (!isOurs(matcher)) return matcher.deepCopy();
    JsonNode inner = matcher.path("parameters").get(ORIGINAL);
    return inner == null || inner.isNull() ? null : inner.deepCopy();
  }

  private static boolean isOurs(JsonNode matcher) {
    return matcher != null && MATCHER.equals(matcher.path("name").asText());
  }
}
