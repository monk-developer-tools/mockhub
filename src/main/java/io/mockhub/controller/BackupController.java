package io.mockhub.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.tomakehurst.wiremock.stubbing.StubMapping;
import io.mockhub.Main;
import io.mockhub.api.ApiRouter;
import io.mockhub.api.Jsons;
import io.mockhub.api.Response;
import io.mockhub.project.Project;
import io.mockhub.project.ProjectService;
import io.mockhub.stub.StubGateway;
import java.util.List;

public final class BackupController implements ApiController {
  private final ProjectService projects;
  private final StubGateway gateway;

  public BackupController(ProjectService projects, StubGateway gateway) {
    this.projects = projects;
    this.gateway = gateway;
  }

  @Override
  public void register(ApiRouter r) {
    r.get("/backup", ctx -> {
      ObjectNode out = Jsons.object();
      out.put("mockhubVersion", Main.VERSION);
      out.set("projects", Jsons.MAPPER.valueToTree(projects.findAll()));
      ArrayNode mappings = out.putArray("mappings");
      gateway.findAll().forEach(s -> mappings.add(Jsons.fromWireMock(s)));
      return Response.download("mockhub-backup.json", "application/json", Jsons.MAPPER.writeValueAsBytes(out));
    });
    r.post("/restore", ctx -> {
      JsonNode body = ctx.json();
      List<Project> list = Jsons.MAPPER.convertValue(body.path("projects"), new TypeReference<List<Project>>() {});
      int projectCount = projects.restore(list == null ? List.of() : list);
      int stubCount = 0;
      for (JsonNode m : body.path("mappings")) {
        StubMapping stub = Jsons.toWireMock(m, StubMapping.class);
        if (gateway.findById(stub.getId()).isPresent()) gateway.update(stub);
        else gateway.add(stub);
        stubCount++;
      }
      return Jsons.object().put("projects", projectCount).put("stubs", stubCount);
    });
  }
}
