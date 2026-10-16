package io.mockhub.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.tomakehurst.wiremock.http.ResponseDefinition;
import io.mockhub.api.ApiRouter;
import io.mockhub.api.Jsons;
import io.mockhub.api.Response;
import io.mockhub.backup.BackupSelection;
import io.mockhub.backup.BackupService;
import io.mockhub.backup.RestoreOptions;
import java.util.ArrayList;
import java.util.List;

public final class BackupController implements ApiController {
  private final BackupService backups;

  public BackupController(BackupService backups) {
    this.backups = backups;
  }

  @Override
  public void register(ApiRouter r) {
    r.get("/backup", ctx -> download(BackupSelection.all()));
    r.post("/backup", ctx -> {
      JsonNode body = ctx.json();
      BackupSelection selection = body.path("everything").asBoolean(false)
          ? BackupSelection.all()
          : BackupSelection.of(ids(body.path("organizations")), ids(body.path("projects")));
      return download(selection);
    });
    r.post("/restore/preview", ctx -> backups.preview(ctx.json()));
    r.post("/restore", ctx -> {
      JsonNode body = ctx.json();
      if (!body.has("backup")) return Jsons.MAPPER.valueToTree(backups.restore(body, RestoreOptions.everything()));
      BackupSelection selection = body.path("everything").asBoolean(false)
          ? BackupSelection.all()
          : BackupSelection.of(ids(body.path("organizations")), ids(body.path("projects")));
      String target = body.path("targetOrgId").asText("");
      RestoreOptions options = new RestoreOptions(selection, target.isBlank() ? null : target, "copy".equals(body.path("mode").asText()));
      return Jsons.MAPPER.valueToTree(backups.restore(body.get("backup"), options));
    });
  }

  private ResponseDefinition download(BackupSelection selection) throws Exception {
    byte[] bytes = Jsons.MAPPER.writerWithDefaultPrettyPrinter().writeValueAsBytes(backups.export(selection));
    return Response.download(backups.fileName(selection), "application/json", bytes);
  }

  private static List<String> ids(JsonNode node) {
    List<String> ids = new ArrayList<>();
    node.forEach(n -> {
      if (n.isTextual() && !n.asText().isBlank()) ids.add(n.asText());
    });
    return ids;
  }
}
