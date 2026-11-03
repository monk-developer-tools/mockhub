package io.mockhub.callout;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public final class CalloutPortability {
  private CalloutPortability() {}

  public static void toWireMockWebhooks(ObjectNode stub) {
    JsonNode listeners = stub.path("serveEventListeners");
    if (!listeners.isArray()) return;
    for (JsonNode listener : listeners) {
      if (!(listener instanceof ObjectNode l) || !CalloutListener.NAME.equals(l.path("name").asText())) continue;
      l.put("name", "webhook");
      if (!(l.get("parameters") instanceof ObjectNode p)) continue;
      JsonNode forward = p.remove("forward");
      if (forward != null && forward.asBoolean()) {
        p.put("method", "{{originalRequest.method}}");
        p.put("body", "{{{originalRequest.body}}}");
      }
    }
  }
}
