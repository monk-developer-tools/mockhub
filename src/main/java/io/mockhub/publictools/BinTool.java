package io.mockhub.publictools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.tomakehurst.wiremock.http.ResponseDefinition;
import io.mockhub.api.Jsons;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class BinTool implements PublicTool {
  public static final int MAX_REPLY_BODY = 4 * 1024;
  private static final Pattern CAPTURE = Pattern.compile("/bins/([a-z0-9]{12})(/.*)?");
  private static final Pattern INSPECT = Pattern.compile("/inspect/([a-z0-9]{12})(/requests|/response)?");

  private final Bins bins;
  private final PublicLimits limits;

  public BinTool(Bins bins, PublicLimits limits) {
    this.bins = bins;
    this.limits = limits;
  }

  @Override
  public Optional<ResponseDefinition> handle(PublicRequest request) {
    if (request.path().equals("/bins") && request.method().equals("POST")) return Optional.of(create(request));
    Matcher capture = CAPTURE.matcher(request.path());
    if (capture.matches()) return Optional.of(capture(capture.group(1), request));
    Matcher inspect = INSPECT.matcher(request.path());
    if (inspect.matches()) return Optional.of(inspect(inspect.group(1), inspect.group(2), request));
    return Optional.empty();
  }

  private ResponseDefinition create(PublicRequest request) {
    Optional<Long> wait = limits.take(request.clientIp(), PublicLimits.Kind.CREATE);
    if (wait.isPresent()) return PublicResponses.tooMany(wait.get(), "new bins");
    return bins.create()
        .map(c -> PublicResponses.json(201, Jsons.object()
            .put("id", c.id())
            .put("token", c.token())
            .put("url", PublicRequest.PREFIX + "/bins/" + c.id())
            .put("expiresAfterIdleHours", Bins.IDLE.toHours())
            .put("keeps", Bins.MAX_REQUESTS)))
        .orElseGet(() -> PublicResponses.error(503, "The demo holds as many bins as it can right now. Try again later."));
  }

  private ResponseDefinition capture(String id, PublicRequest request) {
    return bins.capture(id, request)
        .map(r -> PublicResponses.base(r.status()).withHeader("Content-Type", r.contentType()).withHeader("X-MockHub-Bin", id).withBody(r.body()).build())
        .orElseGet(() -> PublicResponses.error(404, "No bin " + id + ". Bins are deleted 24 hours after their last request."));
  }

  private ResponseDefinition inspect(String id, String part, PublicRequest request) {
    String token = Optional.ofNullable(request.header("X-Bin-Token")).orElse(request.firstQuery("token"));
    String method = request.method();
    if (part == null && method.equals("DELETE")) {
      return bins.clear(id, token) ? PublicResponses.json(200, Jsons.object().put("cleared", true)) : notYours(id);
    }
    if ("/response".equals(part) && method.equals("PUT")) return setReply(id, token, request.body());
    if ("/response".equals(part) && method.equals("GET")) {
      return bins.reply(id, token).map(r -> PublicResponses.json(200, reply(r))).orElseGet(() -> notYours(id));
    }
    if ((part == null || "/requests".equals(part)) && method.equals("GET")) {
      long after = parseLong(request.firstQuery("after"));
      return bins.requests(id, token, after).map(list -> PublicResponses.json(200, listing(id, list))).orElseGet(() -> notYours(id));
    }
    return PublicResponses.error(405, "Use GET to read a bin, PUT /response to set its answer, or DELETE to clear it");
  }

  private ResponseDefinition setReply(String id, String token, String body) {
    JsonNode node;
    try {
      node = Jsons.MAPPER.readTree(body == null || body.isBlank() ? "{}" : body);
    } catch (Exception e) {
      return PublicResponses.error(400, "Send the answer as JSON: {\"status\": 200, \"contentType\": \"application/json\", \"body\": \"...\"}");
    }
    int status = node.path("status").asInt(200);
    if (status < 100 || status > 599) return PublicResponses.error(400, "Use a status from 100 to 599");
    String text = node.path("body").asText("");
    if (text.length() > MAX_REPLY_BODY) return PublicResponses.error(400, "Keep the answer under 4 KB");
    String type = node.path("contentType").asText("application/json");
    Bins.Reply reply = new Bins.Reply(status, type.isBlank() ? "text/plain" : type, text);
    return bins.setReply(id, token, reply) ? PublicResponses.json(200, reply(reply)) : notYours(id);
  }

  private ObjectNode listing(String id, List<Bins.Captured> list) {
    ObjectNode out = Jsons.object().put("id", id);
    bins.expires(id).ifPresent(e -> out.put("expiresAt", e.toString()));
    ArrayNode items = out.putArray("requests");
    for (Bins.Captured c : list) {
      ObjectNode item = items.addObject()
          .put("seq", c.seq()).put("at", c.at().toString()).put("method", c.method()).put("path", c.path())
          .put("query", c.query()).put("body", c.body()).put("size", c.size()).put("truncated", c.truncated());
      ObjectNode headers = item.putObject("headers");
      c.headers().forEach(headers::put);
    }
    return out;
  }

  private static ObjectNode reply(Bins.Reply r) {
    return Jsons.object().put("status", r.status()).put("contentType", r.contentType()).put("body", r.body());
  }

  private static ResponseDefinition notYours(String id) {
    return PublicResponses.error(404, "No bin " + id + " with that token");
  }

  private static long parseLong(String s) {
    try {
      return s == null ? 0 : Long.parseLong(s);
    } catch (NumberFormatException e) {
      return 0;
    }
  }
}
