package io.mockhub.publictools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.mockhub.api.Jsons;
import java.util.Locale;
import java.util.Optional;

public record BinOptions(boolean cors, Optional<RateLimit> rateLimit, Optional<RequiredHeader> requiredHeader, int latencyMs) {
  public static final BinOptions DEFAULT = new BinOptions(true, Optional.empty(), Optional.empty(), 0);
  public static final int MAX_RATE = 10_000;

  public enum Per {
    SECOND(1_000), MINUTE(60_000), HOUR(3_600_000);

    final long millis;

    Per(long millis) {
      this.millis = millis;
    }

    static Per of(String value) {
      try {
        return valueOf(value.trim().toUpperCase(Locale.ROOT));
      } catch (IllegalArgumentException | NullPointerException e) {
        throw new IllegalArgumentException("A rate limit counts per second, minute or hour");
      }
    }
  }

  public record RateLimit(int requests, Per per) {}

  public record RequiredHeader(String name, String value) {
    boolean accepts(String sent) {
      return sent != null && sent.equals(value);
    }
  }

  public static BinOptions from(JsonNode node) {
    if (node == null || !node.isObject()) throw new IllegalArgumentException("Send the settings as a JSON object");
    boolean cors = node.path("cors").asBoolean(true);
    int latency = node.path("latencyMs").asInt(0);
    if (latency < 0 || latency > BinRule.MAX_DELAY_MS) throw new IllegalArgumentException("Default latency goes up to 15 seconds");
    return new BinOptions(cors, rateLimit(node.path("rateLimit")), requiredHeader(node.path("requiredHeader")), latency);
  }

  private static Optional<RateLimit> rateLimit(JsonNode n) {
    if (n.isMissingNode() || n.isNull()) return Optional.empty();
    int requests = n.path("requests").asInt(0);
    if (requests < 1 || requests > MAX_RATE) throw new IllegalArgumentException("A rate limit allows 1 to " + MAX_RATE + " requests");
    return Optional.of(new RateLimit(requests, Per.of(n.path("per").asText("minute"))));
  }

  private static Optional<RequiredHeader> requiredHeader(JsonNode n) {
    if (n.isMissingNode() || n.isNull()) return Optional.empty();
    String name = n.path("name").asText("").trim();
    String value = n.path("value").asText("");
    if (!name.matches("[A-Za-z0-9!#$%&'*+.^_`|~-]{1,100}")) throw new IllegalArgumentException("Name the required header with letters, digits and dashes, like X-Api-Key");
    if (value.isEmpty() || value.length() > 500) throw new IllegalArgumentException("Give the required header a value of 1 to 500 characters");
    return Optional.of(new RequiredHeader(name, value));
  }

  public ObjectNode toJson() {
    ObjectNode out = Jsons.object().put("cors", cors).put("latencyMs", latencyMs);
    rateLimit.ifPresentOrElse(r -> out.putObject("rateLimit").put("requests", r.requests()).put("per", r.per().name().toLowerCase(Locale.ROOT)), () -> out.putNull("rateLimit"));
    requiredHeader.ifPresentOrElse(h -> out.putObject("requiredHeader").put("name", h.name()).put("value", h.value()), () -> out.putNull("requiredHeader"));
    return out;
  }
}
