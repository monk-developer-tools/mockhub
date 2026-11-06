package io.mockhub.publictools;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.mockhub.api.Jsons;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

final class BinHar {
  private BinHar() {}

  static ObjectNode of(String binUrl, List<Bins.Captured> requests) {
    ObjectNode har = Jsons.object();
    ObjectNode log = har.putObject("log").put("version", "1.2");
    log.putObject("creator").put("name", "MockHub webhook bin").put("version", "1");
    ArrayNode entries = log.putArray("entries");
    for (Bins.Captured c : requests) entries.add(entry(binUrl, c));
    return har;
  }

  private static ObjectNode entry(String binUrl, Bins.Captured c) {
    ObjectNode e = Jsons.object().put("startedDateTime", c.at().toString()).put("time", c.outcome() == null ? 0 : c.outcome().millis());
    ObjectNode request = e.putObject("request").put("method", c.method())
        .put("url", binUrl + (c.path().equals("/") ? "" : c.path()) + (c.query().isEmpty() ? "" : "?" + c.query()))
        .put("httpVersion", "HTTP/1.1").put("headersSize", -1).put("bodySize", c.size());
    headers(request.putArray("headers"), c.headers());
    ArrayNode query = request.putArray("queryString");
    PublicRequest.parseQuery(c.query()).forEach((name, values) -> values.forEach(v -> query.addObject().put("name", name).put("value", v)));
    request.putArray("cookies");
    if (!c.body().isEmpty() || !c.raw().isEmpty()) {
      ObjectNode post = request.putObject("postData").put("mimeType", header(c.headers(), "Content-Type"));
      if (c.raw().isEmpty()) post.put("text", c.body());
      else post.put("text", c.raw()).put("encoding", "base64");
    }
    ObjectNode response = e.putObject("response");
    Bins.Outcome o = c.outcome();
    response.put("status", o == null ? 0 : o.status()).put("statusText", "").put("httpVersion", "HTTP/1.1")
        .put("redirectURL", "").put("headersSize", -1).put("bodySize", o == null ? -1 : o.body().getBytes(StandardCharsets.UTF_8).length);
    headers(response.putArray("headers"), o == null ? Map.of() : o.headers());
    response.putArray("cookies");
    response.putObject("content").put("size", o == null ? 0 : o.body().getBytes(StandardCharsets.UTF_8).length)
        .put("mimeType", o == null ? "" : header(o.headers(), "Content-Type")).put("text", o == null ? "" : o.body());
    e.putObject("cache");
    e.putObject("timings").put("send", 0).put("wait", o == null ? 0 : o.millis()).put("receive", 0);
    if (o != null && !o.rule().isEmpty()) e.put("comment", "Answered by rule " + o.rule());
    return e;
  }

  private static void headers(ArrayNode out, Map<String, String> headers) {
    headers.forEach((name, value) -> out.addObject().put("name", name).put("value", value));
  }

  private static String header(Map<String, String> headers, String name) {
    return headers.entrySet().stream().filter(h -> h.getKey().equalsIgnoreCase(name)).map(Map.Entry::getValue).findFirst().orElse("");
  }
}
