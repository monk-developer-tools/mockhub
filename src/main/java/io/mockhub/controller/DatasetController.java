package io.mockhub.controller;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.mockhub.api.ApiException;
import io.mockhub.api.ApiRouter;
import io.mockhub.api.Ctx;
import io.mockhub.api.Jsons;
import io.mockhub.api.Response;
import io.mockhub.dataset.DatasetFormat;
import io.mockhub.dataset.DatasetService;
import io.mockhub.dataset.UploadMode;
import io.mockhub.org.OrganizationService;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public final class DatasetController implements ApiController {
  private static final Set<String> PAGING = Set.of("q", "offset", "limit");
  private static final int MAX_PAGE = 500;

  private final OrganizationService orgs;
  private final DatasetService datasets;

  public DatasetController(OrganizationService orgs, DatasetService datasets) {
    this.orgs = orgs;
    this.datasets = datasets;
  }

  @Override
  public void register(ApiRouter r) {
    r.get("/orgs/{id}/datasets", ctx -> {
      ObjectNode out = Jsons.object();
      out.set("datasets", Jsons.MAPPER.valueToTree(datasets.list(org(ctx))));
      DatasetService.Limits limits = datasets.limits();
      out.putObject("limits").put("maxUploadBytes", limits.maxUploadBytes()).put("maxRecords", limits.maxRecords())
          .put("maxRecordBytes", limits.maxRecordBytes()).put("maxDatasets", limits.maxDatasets())
          .put("maxIdentity", DatasetService.MAX_IDENTITY).put("maxIndexes", DatasetService.MAX_INDEXES);
      return out;
    });
    r.post("/orgs/{id}/datasets", ctx -> Response.json(201, Jsons.MAPPER.valueToTree(datasets.create(org(ctx), ctx.json()))));
    r.get("/orgs/{id}/datasets/{key}", ctx -> Jsons.MAPPER.valueToTree(datasets.get(org(ctx), ctx.path("key"))));
    r.put("/orgs/{id}/datasets/{key}", ctx -> Jsons.MAPPER.valueToTree(datasets.update(org(ctx), ctx.path("key"), ctx.json())));
    r.delete("/orgs/{id}/datasets/{key}", ctx -> {
      if (!datasets.delete(org(ctx), ctx.path("key"))) throw ApiException.notFound("Dataset " + ctx.path("key"));
      return null;
    });
    r.post("/orgs/{id}/datasets/{key}/upload", ctx -> {
      DatasetService.UploadResult result = datasets.upload(org(ctx), ctx.path("key"), DatasetFormat.parse(ctx.query("format")),
          UploadMode.parse(ctx.query("mode")), ctx.bytes());
      ObjectNode out = Jsons.object().put("added", result.added()).put("updated", result.updated()).put("skipped", result.skipped());
      out.set("dataset", Jsons.MAPPER.valueToTree(result.dataset()));
      return out;
    });
    r.get("/orgs/{id}/datasets/{key}/records", ctx -> {
      Map<String, String> filters = new LinkedHashMap<>(ctx.queryParams());
      PAGING.forEach(filters::remove);
      DatasetService.Page page = datasets.records(org(ctx), ctx.path("key"), ctx.query("q"), filters,
          number(ctx, "offset", 0, Integer.MAX_VALUE), number(ctx, "limit", 50, MAX_PAGE));
      ObjectNode out = Jsons.object().put("total", page.total());
      ArrayNode records = out.putArray("records");
      page.records().forEach(records::add);
      return out;
    });
  }

  private String org(Ctx ctx) {
    return orgs.get(ctx.path("id")).id();
  }

  private static int number(Ctx ctx, String name, int fallback, int max) {
    String raw = ctx.query(name);
    if (raw == null || raw.isBlank()) return fallback;
    try {
      int value = Integer.parseInt(raw.trim());
      if (value < 0 || value > max) throw new NumberFormatException();
      return value;
    } catch (NumberFormatException e) {
      throw ApiException.badRequest(name + " must be a whole number from 0 to " + max);
    }
  }
}
