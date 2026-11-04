package io.mockhub.controller;

import io.mockhub.api.ApiException;
import io.mockhub.api.ApiRouter;
import io.mockhub.api.Ctx;
import io.mockhub.api.Jsons;
import io.mockhub.api.Response;
import io.mockhub.project.ProjectService;
import io.mockhub.snapshot.SnapshotService;

public final class SnapshotController implements ApiController {
  private final SnapshotService snapshots;
  private final ProjectAccess access;

  public SnapshotController(SnapshotService snapshots, ProjectService projects) {
    this.snapshots = snapshots;
    this.access = new ProjectAccess(projects);
  }

  @Override
  public void register(ApiRouter r) {
    r.get("/projects/{id}/snapshots/compare", ctx ->
        snapshots.compare(ctx.path("id"), ctx.query("from"), ctx.query("to", SnapshotService.CURRENT)));
    r.post("/projects/{id}/snapshots/upload", ctx ->
        Response.json(201, snapshots.upload(ctx.path("id"), ctx.json(), ctx.principal().label())));
    r.get("/projects/{id}/snapshots", ctx -> Jsons.MAPPER.valueToTree(snapshots.list(ctx.path("id"))));
    r.post("/projects/{id}/snapshots", ctx ->
        Response.json(201, snapshots.create(ctx.path("id"), ctx.json(), ctx.principal().label())));
    r.get("/projects/{id}/snapshots/{snapshotId}", ctx -> snapshots.get(ctx.path("id"), ctx.path("snapshotId")).summary());
    r.delete("/projects/{id}/snapshots/{snapshotId}", ctx -> snapshots.delete(ctx.path("id"), ctx.path("snapshotId")));
    r.get("/projects/{id}/snapshots/{snapshotId}/download", ctx -> {
      SnapshotService.Download file = snapshots.download(ctx.path("id"), ctx.path("snapshotId"));
      return Response.download(file.fileName(), "application/json", file.content());
    });
    r.post("/projects/{id}/snapshots/{snapshotId}/restore", ctx ->
        snapshots.restore(ctx.path("id"), ctx.path("snapshotId"), ctx.principal().label()));
    r.get("/projects/{id}/pull/preview", ctx ->
        snapshots.previewPull(ctx.path("id"), readableSource(ctx, ctx.query("from")), ctx.query("snapshot", SnapshotService.CURRENT)));
    r.post("/projects/{id}/pull", ctx -> snapshots.pull(ctx.path("id"), readableSource(ctx, ctx.json().path("from").asText("")),
        ctx.json().path("snapshot").asText(SnapshotService.CURRENT), ctx.principal().label()));
  }

  private String readableSource(Ctx ctx, String sourceId) {
    if (sourceId == null || sourceId.isBlank()) throw ApiException.badRequest("Choose the project to pull from");
    return access.readable(ctx.principal(), sourceId);
  }
}
