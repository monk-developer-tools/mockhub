package io.mockhub.publictools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.mockhub.api.Jsons;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class RequestEcho {

  private RequestEcho() {}

  public static ObjectNode describe(PublicRequest request, String path) {
    ObjectNode out = Jsons.object();
    out.put("method", request.method());
    out.put("path", path);
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

  public static ObjectNode headers(Map<String, String> headers) {
    ObjectNode node = Jsons.object();
    headers.forEach((k, v) -> {
      String lower = k.toLowerCase(Locale.ROOT);
      if (!lower.startsWith("x-forwarded-") && !lower.equals("x-real-ip")) node.put(k, v);
    });
    return node;
  }
}
