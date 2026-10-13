package io.mockhub.controller;

import io.mockhub.api.ApiRouter;
import io.mockhub.journal.RequestLogService;

public final class RequestLogController implements ApiController {
  private final RequestLogService requestLog;

  public RequestLogController(RequestLogService requestLog) {
    this.requestLog = requestLog;
  }

  @Override
  public void register(ApiRouter r) {
    r.get("/projects/{id}/requests", ctx -> requestLog.list(
        ctx.path("id"),
        Integer.parseInt(ctx.query("limit", "200")),
        Boolean.parseBoolean(ctx.query("unmatched", "false"))));
    r.delete("/projects/{id}/requests", ctx -> {
      requestLog.clear(ctx.path("id"));
      return null;
    });
  }
}
