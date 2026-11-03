package io.mockhub.controller;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.mockhub.api.ApiException;
import io.mockhub.api.ApiRouter;
import io.mockhub.api.Jsons;
import io.mockhub.auth.AuthService;
import io.mockhub.auth.Principal;
import io.mockhub.project.Project;
import io.mockhub.project.ProjectService;
import io.mockhub.token.AccessToken;
import io.mockhub.token.AccessTokenService;
import java.time.Clock;
import java.time.Instant;

public final class AccessTokenController implements ApiController {
  private final AccessTokenService tokens;
  private final AuthService auth;
  private final ProjectService projects;
  private final Clock clock;

  public AccessTokenController(AccessTokenService tokens, AuthService auth, ProjectService projects, Clock clock) {
    this.tokens = tokens;
    this.auth = auth;
    this.projects = projects;
    this.clock = clock;
  }

  @Override
  public void register(ApiRouter r) {
    r.get("/auth/tokens", ctx -> {
      Principal p = signedIn(ctx.principal());
      ArrayNode out = Jsons.MAPPER.createArrayNode();
      tokens.listFor(p.id()).forEach(t -> out.add(view(t)));
      return out;
    });
    r.post("/auth/tokens", ctx -> {
      Principal p = signedIn(ctx.principal());
      AccessTokenService.Created created = tokens.create(p, ctx.json());
      return view(created.token()).put("secret", created.secret());
    });
    r.delete("/auth/tokens/{tokenId}", ctx -> {
      tokens.revoke(signedIn(ctx.principal()), ctx.path("tokenId"));
      return null;
    });
  }

  private Principal signedIn(Principal p) {
    if (!auth.enabled() || p.anonymous()) {
      throw new ApiException(409, "Sign-in is off on this server, so the API and MCP work without a token");
    }
    return p;
  }

  private ObjectNode view(AccessToken t) {
    ObjectNode node = Jsons.object()
        .put("id", t.id())
        .put("name", t.name())
        .put("prefix", t.prefix())
        .put("role", t.role().id())
        .put("mode", t.mode())
        .put("createdAt", t.createdAt())
        .put("expiresAt", t.expiresAt())
        .put("lastUsedAt", t.lastUsedAt())
        .put("expired", t.expiresAt() != null && !Instant.now(clock).isBefore(Instant.parse(t.expiresAt())));
    ArrayNode scope = node.putArray("projects");
    for (String id : t.projectIds()) {
      scope.addObject().put("id", id).put("name", projects.find(id).map(Project::name).orElse(id));
    }
    return node;
  }
}
