package io.mockhub.history;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.mockhub.api.Jsons;
import io.mockhub.playground.Header;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

public final class HarExport {
  private HarExport() {}

  public static ObjectNode of(List<HistoryEntry> entries, String version) {
    ObjectNode har = Jsons.object();
    ObjectNode log = har.putObject("log");
    log.put("version", "1.2");
    log.putObject("creator").put("name", "MockHub").put("version", version);
    ArrayNode out = log.putArray("entries");
    for (HistoryEntry e : entries) out.add(entry(e));
    return har;
  }

  private static ObjectNode entry(HistoryEntry e) {
    ObjectNode entry = Jsons.object();
    entry.put("startedDateTime", Instant.ofEpochMilli(e.at()).toString());
    entry.put("time", e.durationMs());
    ObjectNode request = entry.putObject("request");
    request.put("method", e.method());
    request.put("url", e.absoluteUrl() == null ? e.url() : e.absoluteUrl());
    request.put("httpVersion", "HTTP/1.1");
    request.set("headers", headers(e.requestHeaders()));
    request.set("queryString", query(e.url()));
    request.putArray("cookies");
    request.put("headersSize", -1);
    request.put("bodySize", e.requestBody().length());
    if (!e.requestBody().isEmpty()) {
      request.putObject("postData").put("mimeType", contentType(e.requestHeaders())).put("text", e.requestBody());
    }
    ObjectNode response = entry.putObject("response");
    response.put("status", e.status());
    response.put("statusText", "");
    response.put("httpVersion", "HTTP/1.1");
    response.set("headers", headers(e.responseHeaders()));
    response.putArray("cookies");
    response.putObject("content").put("size", e.responseBody().length()).put("mimeType", contentType(e.responseHeaders()))
        .put("text", e.responseBody());
    response.put("redirectURL", "");
    response.put("headersSize", -1);
    response.put("bodySize", e.responseBody().length());
    entry.putObject("cache");
    entry.putObject("timings").put("send", 0).put("wait", e.durationMs()).put("receive", 0);
    entry.put("comment", e.matched() ? "Matched " + (e.stubName() == null ? "a stub" : e.stubName()) : "No stub matched");
    return entry;
  }

  private static ArrayNode headers(List<Header> headers) {
    ArrayNode out = Jsons.MAPPER.createArrayNode();
    headers.forEach(h -> out.addObject().put("name", h.name()).put("value", h.value()));
    return out;
  }

  private static ArrayNode query(String url) {
    ArrayNode out = Jsons.MAPPER.createArrayNode();
    String raw;
    try {
      raw = URI.create(url).getRawQuery();
    } catch (IllegalArgumentException e) {
      int q = url.indexOf('?');
      raw = q < 0 ? null : url.substring(q + 1);
    }
    if (raw == null || raw.isEmpty()) return out;
    for (String pair : raw.split("&")) {
      int eq = pair.indexOf('=');
      String name = eq < 0 ? pair : pair.substring(0, eq);
      String value = eq < 0 ? "" : pair.substring(eq + 1);
      out.addObject().put("name", decode(name)).put("value", decode(value));
    }
    return out;
  }

  private static String decode(String s) {
    try {
      return URLDecoder.decode(s, StandardCharsets.UTF_8);
    } catch (IllegalArgumentException e) {
      return s;
    }
  }

  private static String contentType(List<Header> headers) {
    return headers.stream().filter(h -> h.name().equalsIgnoreCase("Content-Type")).map(Header::value).findFirst().orElse("");
  }
}
