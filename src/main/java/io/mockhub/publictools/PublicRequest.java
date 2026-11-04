package io.mockhub.publictools;

import com.github.tomakehurst.wiremock.common.Urls;
import com.github.tomakehurst.wiremock.http.HttpHeader;
import com.github.tomakehurst.wiremock.http.Request;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public record PublicRequest(String method, String path, String rawQuery, Map<String, List<String>> query,
    Map<String, String> headers, String body, int bodySize, String clientIp) {

  public static final String PREFIX = "/__public";

  public static PublicRequest from(Request request) {
    String url = request.getUrl();
    String path = Urls.getPath(url);
    int at = url.indexOf('?');
    String rawQuery = at < 0 ? "" : url.substring(at + 1);
    Map<String, String> headers = new LinkedHashMap<>();
    for (HttpHeader h : request.getHeaders().all()) headers.put(h.key(), String.join(", ", h.values()));
    byte[] body = request.getBody();
    return new PublicRequest(request.getMethod().getName().toUpperCase(Locale.ROOT), path.substring(PREFIX.length()), rawQuery,
        parseQuery(rawQuery), headers, request.getBodyAsString(), body == null ? 0 : body.length, request.getClientIp());
  }

  static Map<String, List<String>> parseQuery(String raw) {
    Map<String, List<String>> out = new LinkedHashMap<>();
    if (raw.isEmpty()) return out;
    for (String pair : raw.split("&")) {
      if (pair.isEmpty()) continue;
      int eq = pair.indexOf('=');
      String key = decode(eq < 0 ? pair : pair.substring(0, eq));
      String value = eq < 0 ? "" : decode(pair.substring(eq + 1));
      out.computeIfAbsent(key, k -> new ArrayList<>()).add(value);
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

  public String header(String name) {
    return headers.entrySet().stream().filter(e -> e.getKey().equalsIgnoreCase(name)).map(Map.Entry::getValue).findFirst().orElse(null);
  }

  public String firstQuery(String name) {
    List<String> values = query.get(name);
    return values == null || values.isEmpty() ? null : values.get(0);
  }
}
