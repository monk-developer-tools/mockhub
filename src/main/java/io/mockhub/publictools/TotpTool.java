package io.mockhub.publictools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.tomakehurst.wiremock.http.ResponseDefinition;
import io.mockhub.api.Jsons;
import java.time.Instant;
import java.util.Optional;
import java.util.function.LongSupplier;

public final class TotpTool implements PublicTool {
  static final int MAX_REQUEST = 4 * 1024;
  static final long MAX_TIME = 253_402_300_799L;

  private final LongSupplier millis;

  public TotpTool(LongSupplier millis) {
    this.millis = millis;
  }

  @Override
  public Optional<ResponseDefinition> handle(PublicRequest request) {
    if (!request.path().equals("/totp")) return Optional.empty();
    if (!request.method().equals("GET") && !request.method().equals("POST")) {
      return Optional.of(PublicResponses.error(405, "Send a GET with ?secret= or a POST with {\"secret\": \"...\"}"));
    }
    if (request.bodySize() > MAX_REQUEST) return Optional.of(PublicResponses.error(413, "Keep the request under 4 KB"));
    try {
      return Optional.of(answer(request));
    } catch (IllegalArgumentException e) {
      return Optional.of(PublicResponses.error(400, e.getMessage()));
    }
  }

  private ResponseDefinition answer(PublicRequest request) {
    JsonNode body = request.method().equals("POST") ? PublicJson.parse(request.body()) : null;
    if (request.method().equals("POST") && (body == null || !body.isObject())) {
      throw new IllegalArgumentException("Send JSON like {\"secret\": \"JBSWY3DPEHPK3PXP\"} or {\"uri\": \"otpauth://totp/...\"}");
    }
    TotpParams params = TotpParams.of(new TotpParams.Input(field(request, body, "secret"), field(request, body, "uri"),
        field(request, body, "digits"), field(request, body, "period"), field(request, body, "algorithm")));
    long now = time(field(request, body, "time"));
    long step = Math.floorDiv(now, params.period());
    long from = step * params.period();
    String code = Totp.code(params, now);
    if ("text".equalsIgnoreCase(field(request, body, "format"))) {
      return PublicResponses.base(200).withHeader("Content-Type", "text/plain; charset=utf-8").withBody(code).build();
    }
    ObjectNode out = Jsons.object()
        .put("code", code)
        .put("secondsRemaining", from + params.period() - now)
        .put("validFrom", Instant.ofEpochSecond(from).toString())
        .put("validUntil", Instant.ofEpochSecond(from + params.period()).toString())
        .put("previous", Totp.code(params, from - params.period()))
        .put("next", Totp.code(params, from + params.period()))
        .put("digits", params.digits())
        .put("period", params.period())
        .put("algorithm", params.algorithm());
    if (!params.issuer().isEmpty()) out.put("issuer", params.issuer());
    if (!params.account().isEmpty()) out.put("account", params.account());
    return PublicResponses.json(200, out);
  }

  private long time(String value) {
    if (value == null || value.isBlank()) return Math.floorDiv(millis.getAsLong(), 1000L);
    try {
      long at = Long.parseLong(value.trim());
      if (at < 0 || at > MAX_TIME) throw new IllegalArgumentException("time must be Unix seconds between 0 and " + MAX_TIME);
      return at;
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException("time must be Unix seconds, like 1700000000");
    }
  }

  private static String field(PublicRequest request, JsonNode body, String name) {
    if (body != null) {
      JsonNode value = body.get(name);
      return value == null || value.isNull() ? null : value.asText();
    }
    return request.firstQuery(name);
  }
}
