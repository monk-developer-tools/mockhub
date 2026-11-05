package io.mockhub.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.mockhub.api.ApiRouter;
import io.mockhub.api.Jsons;
import io.mockhub.importer.ImporterRegistry;
import io.mockhub.importer.SpecFetcher;
import io.mockhub.importer.StubImporter;
import io.mockhub.project.Project;
import java.util.Map;

public final class OpenApiLinkController implements ApiController {
  private static final Project PREVIEW = new Project("openapi-preview", "Preview", "openapi-preview", "", null, null, null, null, null, null, false, null);

  private final SpecFetcher fetcher;
  private final ImporterRegistry importers;

  public OpenApiLinkController(SpecFetcher fetcher, ImporterRegistry importers) {
    this.fetcher = fetcher;
    this.importers = importers;
  }

  @Override
  public void register(ApiRouter r) {
    r.post("/openapi/fetch", ctx -> {
      String url = ctx.json().path("url").asText("");
      String content = fetcher.fetch(url);
      JsonNode details = importers.get("openapi").parse(new StubImporter.Request(PREVIEW, content, Map.of("limit", "0"))).details();
      ObjectNode out = Jsons.object();
      out.put("url", url.trim());
      out.put("title", details.path("title").asText(""));
      out.put("version", details.path("version").asText(""));
      out.put("operations", details.path("operations").size());
      out.put("content", content);
      return out;
    });
  }
}
