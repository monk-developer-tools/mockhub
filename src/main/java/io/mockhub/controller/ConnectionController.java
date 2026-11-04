package io.mockhub.controller;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.mockhub.api.ApiException;
import io.mockhub.api.ApiRouter;
import io.mockhub.api.Ctx;
import io.mockhub.api.Jsons;
import io.mockhub.api.Response;
import io.mockhub.connection.ConnectionException;
import io.mockhub.connection.ConnectionService;
import io.mockhub.org.OrganizationService;

public final class ConnectionController implements ApiController {
  private final OrganizationService orgs;
  private final ConnectionService connections;

  public ConnectionController(OrganizationService orgs, ConnectionService connections) {
    this.orgs = orgs;
    this.connections = connections;
  }

  @Override
  public void register(ApiRouter r) {
    r.get("/orgs/{id}/connections", ctx -> {
      ArrayNode out = Jsons.MAPPER.createArrayNode();
      connections.list(org(ctx)).forEach(c -> out.add(ConnectionService.view(c)));
      return out;
    });
    r.post("/orgs/{id}/connections", ctx -> Response.json(201, ConnectionService.view(connections.create(org(ctx), ctx.json()))));
    r.get("/orgs/{id}/connections/{key}", ctx -> ConnectionService.view(connections.get(org(ctx), ctx.path("key"))));
    r.put("/orgs/{id}/connections/{key}", ctx -> ConnectionService.view(connections.update(org(ctx), ctx.path("key"), ctx.json())));
    r.delete("/orgs/{id}/connections/{key}", ctx -> {
      if (!connections.delete(org(ctx), ctx.path("key"))) throw ApiException.notFound("Connection " + ctx.path("key"));
      return null;
    });
    r.post("/orgs/{id}/connections/{key}/test", ctx -> {
      try {
        ConnectionService.TestResult t = connections.test(org(ctx), ctx.path("key"));
        ObjectNode out = Jsons.object().put("ok", true).put("headerName", t.headerName()).put("preview", t.preview());
        if (!t.tokenType().isEmpty()) out.put("tokenType", t.tokenType()).put("expiresIn", t.expiresInSeconds()).put("scope", t.scope());
        return out;
      } catch (ConnectionException e) {
        throw ApiException.badRequest(e.getMessage());
      }
    });
  }

  private String org(Ctx ctx) {
    return orgs.get(ctx.path("id")).id();
  }
}
