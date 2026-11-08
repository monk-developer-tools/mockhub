package io.mockhub.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.tomakehurst.wiremock.admin.AdminTask;
import com.github.tomakehurst.wiremock.http.HttpHeader;
import com.github.tomakehurst.wiremock.http.ImmutableRequest;
import com.github.tomakehurst.wiremock.http.Request;
import com.github.tomakehurst.wiremock.http.RequestMethod;
import com.github.tomakehurst.wiremock.http.ResponseDefinition;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import io.mockhub.api.AdminPaths;
import io.mockhub.api.Jsons;
import io.mockhub.auth.AccessPolicy;
import io.mockhub.auth.Principal;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;

public final class InternalApi {
  private static final Set<String> NOT_COPIED = Set.of("content-length", "content-type", "transfer-encoding", "accept", "mcp-session-id", "mcp-protocol-version");

  public record Result(int status, String body) {
    public boolean ok() {
      return status < 400;
    }

    public JsonNode json() {
      try {
        return body == null || body.isBlank() ? Jsons.object() : Jsons.MAPPER.readTree(body);
      } catch (Exception e) {
        return Jsons.object().put("text", body);
      }
    }

    public String error() {
      JsonNode node = json();
      return node.path("error").asText(body);
    }
  }

  private final AdminTask api;
  private final AccessPolicy policy;

  public InternalApi(AdminTask api, AccessPolicy policy) {
    this.api = api;
    this.policy = policy;
  }

  public Result call(Request original, Principal principal, RequestMethod method, String pathAndQuery, JsonNode body) {
    return body == null
        ? call(original, principal, method, pathAndQuery, null, null)
        : call(original, principal, method, pathAndQuery, "application/json", Jsons.write(body).getBytes(StandardCharsets.UTF_8));
  }

  public Result call(Request original, Principal principal, RequestMethod method, String pathAndQuery, String contentType, byte[] body) {
    String path = pathAndQuery.contains("?") ? pathAndQuery.substring(0, pathAndQuery.indexOf('?')) : pathAndQuery;
    AccessPolicy.Decision decision = policy.decide(principal, method, AdminPaths.API_ROOT + path);
    if (!decision.allowed()) return new Result(403, Jsons.write(Jsons.object().put("error", decision.reason())));
    ImmutableRequest.Builder request = ImmutableRequest.create()
        .withAbsoluteUrl(origin(original) + AdminPaths.ADMIN_ROOT + AdminPaths.API_ROOT + pathAndQuery)
        .withMethod(method)
        .withClientIp(original.getClientIp())
        .withProtocol(original.getProtocol());
    for (HttpHeader header : original.getHeaders().all()) {
      if (!NOT_COPIED.contains(header.key().toLowerCase(Locale.ROOT))) request.withHeader(header.key(), header.values());
    }
    request.withHeader("Accept", "application/json");
    if (body != null) {
      request.withHeader("Content-Type", contentType);
      request.withBody(body);
    } else {
      request.withBody(new byte[0]);
    }
    ResponseDefinition response = api.execute(null, ServeEvent.of(request.build()), null);
    return new Result(response.getStatus(), response.getBody());
  }

  private static String origin(Request request) {
    String url = request.getAbsoluteUrl();
    int pathStart = url.indexOf('/', url.indexOf("//") + 2);
    return pathStart < 0 ? url : url.substring(0, pathStart);
  }
}
