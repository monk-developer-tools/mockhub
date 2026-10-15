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
import io.mockhub.org.Organization;
import io.mockhub.org.OrganizationService;
import io.mockhub.project.Project;
import io.mockhub.project.ProjectService;
import io.mockhub.stub.StubGateway;
import java.util.List;

public final class BackupController implements ApiController {
  private final OrganizationService orgs;
  private final ProjectService projects;
  private final StubGateway gateway;

  public BackupController(OrganizationService orgs, ProjectService projects, StubGateway gateway) {
    this.orgs = orgs;
    this.projects = projects;
    this.gateway = gateway;
  }

  @Override
  public void register(ApiRouter r) {
    r.get("/backup", ctx -> {
      ObjectNode out = Jsons.object();
      out.put("mockhubVersion", Main.VERSION);
      out.set("organizations", Jsons.MAPPER.valueToTree(orgs.findAll()));
      out.set("projects", Jsons.MAPPER.valueToTree(projects.findAll()));
      ArrayNode mappings = out.putArray("mappings");
      gateway.findAll().forEach(s -> mappings.add(Jsons.fromWireMock(s)));
      return Response.download("mockhub-backup.json", "application/json", Jsons.MAPPER.writeValueAsBytes(out));
    });
    r.post("/restore", ctx -> {
      JsonNode body = ctx.json();
      List<Organization> orgList = Jsons.MAPPER.convertValue(body.path("organizations"), new TypeReference<List<Organization>>() {});
      int orgCount = orgs.restore(orgList == null ? List.of() : orgList);
      List<Project> list = Jsons.MAPPER.convertValue(body.path("projects"), new TypeReference<List<Project>>() {});
      int projectCount = projects.restore(list == null ? List.of() : list);
      projects.assignOrphans(orgs.ensureDefault().id());
      int stubCount = 0;
      for (JsonNode m : body.path("mappings")) {
        StubMapping stub = Jsons.toWireMock(m, StubMapping.class);
        if (gateway.findById(stub.getId()).isPresent()) gateway.update(stub);
        else gateway.add(stub);
        stubCount++;
      }
      return Jsons.object().put("organizations", orgCount).put("projects", projectCount).put("stubs", stubCount);
    });
  }
}
