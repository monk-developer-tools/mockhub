package io.mockhub.controller;

import io.mockhub.api.ApiRouter;
import io.mockhub.api.Jsons;
import io.mockhub.callout.CalloutLog;

public final class CalloutController implements ApiController {
  private final CalloutLog log;

  public CalloutController(CalloutLog log) {
    this.log = log;
  }

  @Override
  public void register(ApiRouter r) {
    r.get("/projects/{id}/callouts", ctx -> Jsons.MAPPER.valueToTree(log.list(ctx.path("id"), Integer.parseInt(ctx.query("limit", "200")))));
    r.delete("/projects/{id}/callouts", ctx -> {
      log.clear(ctx.path("id"));
      return null;
    });
  }
}
