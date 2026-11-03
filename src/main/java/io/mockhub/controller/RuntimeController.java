package io.mockhub.controller;

import io.mockhub.api.ApiRouter;
import io.mockhub.api.Jsons;
import io.mockhub.runtime.RuntimeService;

public final class RuntimeController implements ApiController {
  private final RuntimeService runtime;

  public RuntimeController(RuntimeService runtime) {
    this.runtime = runtime;
  }

  @Override
  public void register(ApiRouter r) {
    r.get("/projects/{id}/runtime", ctx -> Jsons.MAPPER.valueToTree(runtime.current(ctx.path("id"))));
    r.put("/projects/{id}/runtime", ctx -> Jsons.MAPPER.valueToTree(runtime.update(ctx.path("id"), ctx.json())));
  }
}
