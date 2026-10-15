package io.mockhub.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.tomakehurst.wiremock.http.QueryParameter;
import com.github.tomakehurst.wiremock.http.Request;
import io.mockhub.auth.Principal;
import java.util.Map;
import java.util.function.Function;

public final class Ctx {
  public final Request request;
  private final Map<String, String> pathParams;
  private final Function<Request, Principal> principals;
  private Principal principal;

  Ctx(Request request, Map<String, String> pathParams, Function<Request, Principal> principals) {
    this.request = request;
    this.pathParams = pathParams;
    this.principals = principals;
  }

  public Principal principal() {
    if (principal == null) principal = principals.apply(request);
    if (principal == null) throw new ApiException(401, "Authentication required");
    return principal;
  }

  public String path(String name) {
    return pathParams.get(name);
  }

  public String query(String name) {
    QueryParameter p = request.queryParameter(name);
    return p != null && p.isPresent() ? p.firstValue() : null;
  }

  public Map<String, String> queryParams() {
    Map<String, String> out = new java.util.LinkedHashMap<>();
    String url = request.getUrl();
    int q = url.indexOf('?');
    if (q < 0) return out;
    for (String pair : url.substring(q + 1).split("&")) {
      if (pair.isEmpty()) continue;
      int eq = pair.indexOf('=');
      String k = java.net.URLDecoder.decode(eq < 0 ? pair : pair.substring(0, eq), java.nio.charset.StandardCharsets.UTF_8);
      String v = eq < 0 ? "" : java.net.URLDecoder.decode(pair.substring(eq + 1), java.nio.charset.StandardCharsets.UTF_8);
      out.putIfAbsent(k, v);
    }
    return out;
  }

  public String query(String name, String fallback) {
    String v = query(name);
    return v == null || v.isEmpty() ? fallback : v;
  }

  public String body() {
    return request.getBodyAsString();
  }

  public JsonNode json() {
    String body = body();
    if (body == null || body.isBlank()) {
      throw ApiException.badRequest("Request body is required");
    }
    try {
      return Jsons.MAPPER.readTree(body);
    } catch (Exception e) {
      throw ApiException.badRequest("Invalid JSON: " + e.getMessage());
    }
  }

  public String baseUrl(String configuredPublicUrl) {
    if (configuredPublicUrl != null && !configuredPublicUrl.isEmpty()) return configuredPublicUrl;
    String proto = request.getHeader("X-Forwarded-Proto");
    String host = request.getHeader("X-Forwarded-Host");
    if (host == null) host = request.getHeader("Host");
    if (proto == null) proto = request.getScheme();
    if (host == null) host = request.getHost() + ":" + request.getPort();
    return proto + "://" + host;
  }
}
