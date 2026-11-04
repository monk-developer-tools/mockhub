package io.mockhub.publictools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.http.ResponseDefinition;
import io.mockhub.api.Jsons;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class EchoTool implements PublicTool {
  public static final int MAX_DELAY_MS = 10_000;
  public static final int MAX_BODY = 1024 * 1024;
  private static final Pattern STATUS = Pattern.compile("/status/(\\d{3})");
  private static final Pattern DELAY = Pattern.compile("/delay/(\\d{1,6})");

  @Override
  public Optional<ResponseDefinition> handle(PublicRequest request) {
    String path = request.path();
    if (path.equals("/echo") || path.startsWith("/echo/")) return Optional.of(echo(request));
    if (path.equals("/headers")) return Optional.of(PublicResponses.json(200, Jsons.object().set("headers", headers(request.headers()))));
    Matcher status = STATUS.matcher(path);
    if (status.matches()) return Optional.of(status(Integer.parseInt(status.group(1))));
    Matcher delay = DELAY.matcher(path);
    if (delay.matches()) return Optional.of(delay(Integer.parseInt(delay.group(1)), request));
    return Optional.empty();
  }

  private static ResponseDefinition echo(PublicRequest request) {
    if (request.bodySize() > MAX_BODY) return PublicResponses.error(413, "The echo takes bodies up to 1 MB");
    return PublicResponses.json(200, describe(request));
  }

  static ObjectNode describe(PublicRequest request) {
    ObjectNode out = Jsons.object();
    out.put("method", request.method());
    out.put("path", request.path().replaceFirst("^/echo", "").isEmpty() ? "/" : request.path().replaceFirst("^/echo", ""));
    ObjectNode query = out.putObject("query");
    request.query().forEach((k, v) -> {
      if (v.size() == 1) query.put(k, v.get(0));
      else v.forEach(query.withArray(k)::add);
    });
    out.set("headers", headers(request.headers()));
    String type = Optional.ofNullable(request.header("Content-Type")).orElse("").toLowerCase(Locale.ROOT);
    out.put("bodySize", request.bodySize());
    if (request.bodySize() > 0) {
      out.put("body", request.body());
      JsonNode json = type.contains("json") ? PublicJson.parse(request.body()) : null;
      if (json != null) out.set("json", json);
      if (type.contains("application/x-www-form-urlencoded")) {
        ObjectNode form = out.putObject("form");
        PublicRequest.parseQuery(request.body()).forEach((k, v) -> form.put(k, String.join(",", v)));
      }
    }
    return out;
  }

  private static ObjectNode headers(Map<String, String> headers) {
    ObjectNode node = Jsons.object();
    headers.forEach((k, v) -> {
      String lower = k.toLowerCase(Locale.ROOT);
      if (!lower.startsWith("x-forwarded-") && !lower.equals("x-real-ip")) node.put(k, v);
    });
    return node;
  }

  private static ResponseDefinition status(int code) {
    if (code < 100 || code > 599) return PublicResponses.error(400, "Use a status from 100 to 599");
    ResponseDefinitionBuilder builder = PublicResponses.base(code);
    if (code == 204 || code == 304 || code < 200) return builder.build();
    return builder.withHeader("Content-Type", "application/json")
        .withBody(Jsons.write(Jsons.object().put("status", code))).build();
  }

  private static ResponseDefinition delay(int ms, PublicRequest request) {
    if (ms > MAX_DELAY_MS) return PublicResponses.error(400, "Delays go up to " + MAX_DELAY_MS + " ms");
    return PublicResponses.base(200).withHeader("Content-Type", "application/json")
        .withBody(Jsons.write(Jsons.object().put("delayedMs", ms).put("method", request.method())))
        .withFixedDelay(ms).build();
  }
}
