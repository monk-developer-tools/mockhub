package io.mockhub.controller;

import io.mockhub.api.ApiException;
import io.mockhub.api.ApiRouter;
import io.mockhub.api.Jsons;
import io.mockhub.api.Response;
import io.mockhub.history.HarExport;
import io.mockhub.history.HistoryEntry;
import io.mockhub.history.HistoryQuery;
import io.mockhub.history.HistoryStore;
import io.mockhub.history.TrafficCounters;
import io.mockhub.project.Project;
import io.mockhub.project.ProjectService;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

public final class HistoryController implements ApiController {
  private final ProjectService projects;
  private final HistoryStore history;
  private final TrafficCounters traffic;
  private final String version;

  public HistoryController(ProjectService projects, HistoryStore history, TrafficCounters traffic, String version) {
    this.projects = projects;
    this.history = history;
    this.traffic = traffic;
    this.version = version;
  }

  @Override
  public void register(ApiRouter r) {
    r.get("/projects/{id}/history", ctx -> {
      Project p = projects.get(ctx.path("id"));
      return Jsons.MAPPER.valueToTree(history.search(p.id(), HistoryQuery.parse(ctx::query)));
    });
    r.get("/projects/{id}/history/{entryId}", ctx -> {
      Project p = projects.get(ctx.path("id"));
      HistoryEntry entry = history.get(p.id(), ctx.path("entryId"))
          .orElseThrow(() -> ApiException.notFound("Request " + ctx.path("entryId") + " is no longer in the history"));
      var out = Jsons.object();
      out.set("entry", Jsons.MAPPER.valueToTree(entry));
      out.set("callouts", Jsons.MAPPER.valueToTree(history.callouts(p.id(), 50, entry.id())));
      return out;
    });
    r.get("/projects/{id}/history-export", ctx -> {
      Project p = projects.get(ctx.path("id"));
      HistoryQuery query = HistoryQuery.parse(ctx::query);
      List<HistoryEntry> entries = history.search(p.id(), query, true).items();
      byte[] har = Jsons.write(HarExport.of(entries, version)).getBytes(StandardCharsets.UTF_8);
      String name = p.slug() + "-" + LocalDate.now(ZoneOffset.UTC) + ".har";
      return Response.download(name, "application/json", har);
    });
    r.get("/projects/{id}/traffic", ctx -> {
      Project p = projects.get(ctx.path("id"));
      return Jsons.MAPPER.valueToTree(traffic.view(p.id(), ctx.query("window")));
    });
    r.get("/projects/{id}/traffic/daily", ctx -> {
      Project p = projects.get(ctx.path("id"));
      return Jsons.MAPPER.valueToTree(traffic.daily(p.id()));
    });
  }
}
