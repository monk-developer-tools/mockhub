package io.mockhub.controller;

import io.mockhub.api.ApiRouter;
import io.mockhub.api.Jsons;
import io.mockhub.project.Project;
import io.mockhub.project.ProjectService;
import io.mockhub.runtime.DefaultResponseStubs;
import io.mockhub.runtime.RateLimiter;
import io.mockhub.runtime.RuntimeSettings;

public final class RuntimeController implements ApiController {
  private final ProjectService projects;
  private final DefaultResponseStubs defaultResponses;
  private final RateLimiter limiter;

  public RuntimeController(ProjectService projects, DefaultResponseStubs defaultResponses, RateLimiter limiter) {
    this.projects = projects;
    this.defaultResponses = defaultResponses;
    this.limiter = limiter;
  }

  @Override
  public void register(ApiRouter r) {
    r.get("/projects/{id}/runtime", ctx -> Jsons.MAPPER.valueToTree(RuntimeSettings.of(projects.get(ctx.path("id")).runtime())));
    r.put("/projects/{id}/runtime", ctx -> {
      RuntimeSettings settings = RuntimeSettings.parse(ctx.json());
      Project updated = projects.updateRuntime(ctx.path("id"), settings);
      defaultResponses.sync(updated);
      limiter.forget(updated.id());
      return Jsons.MAPPER.valueToTree(settings);
    });
  }
}
