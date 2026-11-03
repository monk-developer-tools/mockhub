package io.mockhub.controller;

import io.mockhub.api.ApiRouter;
import io.mockhub.history.HistoryStore;
import io.mockhub.journal.RequestLogService;

public final class RequestLogController implements ApiController {
  private final RequestLogService requestLog;
  private final HistoryStore history;

  public RequestLogController(RequestLogService requestLog, HistoryStore history) {
    this.requestLog = requestLog;
    this.history = history;
  }

  @Override
  public void register(ApiRouter r) {
    r.get("/projects/{id}/usage", ctx -> requestLog.usage(ctx.path("id")));
    r.get("/projects/{id}/requests", ctx -> requestLog.list(
        ctx.path("id"),
        Integer.parseInt(ctx.query("limit", "200")),
        Boolean.parseBoolean(ctx.query("unmatched", "false"))));
    r.delete("/projects/{id}/requests", ctx -> {
      requestLog.clear(ctx.path("id"));
      history.clearRequests(ctx.path("id"));
      return null;
    });
  }
}
