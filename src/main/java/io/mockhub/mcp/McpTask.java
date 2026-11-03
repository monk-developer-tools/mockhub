package io.mockhub.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.github.tomakehurst.wiremock.admin.AdminTask;
import com.github.tomakehurst.wiremock.common.url.PathParams;
import com.github.tomakehurst.wiremock.core.Admin;
import com.github.tomakehurst.wiremock.http.Request;
import com.github.tomakehurst.wiremock.http.ResponseDefinition;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import io.mockhub.api.Jsons;
import io.mockhub.api.Response;
import io.mockhub.auth.Principal;
import java.net.URI;
import java.util.function.Function;

import static com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder.responseDefinition;

public final class McpTask implements AdminTask {
  private final McpServer server;
  private final Function<Request, Principal> principals;

  public McpTask(McpServer server, Function<Request, Principal> principals) {
    this.server = server;
    this.principals = principals;
  }

  @Override
  public ResponseDefinition execute(Admin admin, ServeEvent serveEvent, PathParams pathParams) {
    Request request = serveEvent.getRequest();
    if (!sameOrigin(request)) return Response.error(403, "MCP requests from web pages on other sites are refused");
    Principal principal = principals.apply(request);
    if (principal == null) return Response.error(401, "Authentication required");
    JsonNode message;
    try {
      message = Jsons.MAPPER.readTree(request.getBodyAsString());
    } catch (Exception e) {
      return Response.json(400, McpServer.error(null, McpServer.PARSE_ERROR, "Invalid JSON"));
    }
    if (message == null) return Response.json(400, McpServer.error(null, McpServer.PARSE_ERROR, "Empty body"));
    if (message.isArray()) {
      ArrayNode replies = Jsons.MAPPER.createArrayNode();
      message.forEach(m -> {
        JsonNode reply = server.handle(m, request, principal);
        if (reply != null) replies.add(reply);
      });
      return replies.isEmpty() ? accepted() : Response.json(replies);
    }
    JsonNode reply = server.handle(message, request, principal);
    return reply == null ? accepted() : Response.json(reply);
  }

  private static ResponseDefinition accepted() {
    return responseDefinition().withStatus(202).build();
  }

  static boolean sameOrigin(Request request) {
    String origin = request.getHeader("Origin");
    if (origin == null || origin.isBlank() || origin.equals("null")) return true;
    String host = request.getHeader("X-Forwarded-Host");
    if (host == null) host = request.getHeader("Host");
    try {
      String originHost = URI.create(origin).getAuthority();
      return originHost != null && originHost.equalsIgnoreCase(host);
    } catch (IllegalArgumentException e) {
      return false;
    }
  }
}
