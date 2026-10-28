package io.mockhub.controller;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.mockhub.api.ApiException;
import io.mockhub.api.ApiRouter;
import io.mockhub.api.Response;
import io.mockhub.export.CollectionExporter;
import io.mockhub.export.ExporterRegistry;
import io.mockhub.api.Jsons;
import io.mockhub.importer.ImporterRegistry;
import io.mockhub.importer.WireMockArchiveImporter;
import io.mockhub.importer.StubImporter;
import io.mockhub.project.Project;
import io.mockhub.project.ProjectService;
import io.mockhub.stub.StubService;
import java.util.List;

public final class ImportExportController implements ApiController {
  private final ProjectService projects;
  private final StubService stubs;
  private final ExporterRegistry exporters;
  private final ImporterRegistry importers;
  private final BaseUrls urls;
  private final WireMockArchiveImporter archives;

  public ImportExportController(ProjectService projects, StubService stubs, ExporterRegistry exporters,
      ImporterRegistry importers, BaseUrls urls, WireMockArchiveImporter archives) {
    this.archives = archives;
    this.projects = projects;
    this.stubs = stubs;
    this.exporters = exporters;
    this.importers = importers;
    this.urls = urls;
  }

  @Override
  public void register(ApiRouter r) {
    r.get("/projects/{id}/export/{format}", ctx -> {
      Project p = projects.get(ctx.path("id"));
      CollectionExporter exporter = exporters.get(ctx.path("format"));
      byte[] content = exporter.export(p, stubs.stubsOf(p.id()), urls.mock(ctx, p));
      return Response.download(exporter.fileName(p), exporter.contentType(), content);
    });
    r.post("/projects/{id}/import/wiremock-zip", ctx -> {
      Project p = projects.get(ctx.path("id"));
      WireMockArchiveImporter.Result result = archives.parse(p, ctx.bytes());
      List<ObjectNode> created = stubs.createAll(p.id(), result.stubs());
      ObjectNode out = Jsons.object();
      out.put("imported", created.size());
      out.put("files", result.files());
      ArrayNode missing = out.putArray("missingFiles");
      result.missingFiles().forEach(missing::add);
      return out;
    });
    r.post("/projects/{id}/import/{format}", ctx -> {
      Project p = projects.get(ctx.path("id"));
      String body = ctx.body();
      if (body == null || body.isBlank()) throw ApiException.badRequest("Send the document to import as the request body");
      StubImporter.Result result = importers.get(ctx.path("format")).parse(new StubImporter.Request(p, body, ctx.queryParams()));
      List<ObjectNode> created = stubs.createAll(p.id(), result.stubs());
      ObjectNode out = result.details().deepCopy();
      out.put("imported", created.size());
      return out;
    });
  }
}
