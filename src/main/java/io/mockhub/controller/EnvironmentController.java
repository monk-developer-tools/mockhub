package io.mockhub.controller;

import io.mockhub.api.ApiRouter;
import io.mockhub.api.Jsons;
import io.mockhub.environment.EnvironmentService;

public final class EnvironmentController implements ApiController {
  private final EnvironmentService environments;

  public EnvironmentController(EnvironmentService environments) {
    this.environments = environments;
  }

  @Override
  public void register(ApiRouter r) {
    r.get("/projects/{id}/environments", ctx -> Jsons.MAPPER.valueToTree(environments.get(ctx.path("id"))));
    r.put("/projects/{id}/environments", ctx -> Jsons.MAPPER.valueToTree(environments.replace(ctx.path("id"), ctx.json())));
    r.put("/projects/{id}/environments/active", ctx ->
        Jsons.MAPPER.valueToTree(environments.switchTo(ctx.path("id"), ctx.json() == null ? null : ctx.json().path("name").asText(null))));
  }
}
