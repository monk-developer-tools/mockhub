package io.mockhub.controller;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.tomakehurst.wiremock.stubbing.StubMapping;
import io.mockhub.api.ApiException;
import io.mockhub.api.ApiRouter;
import io.mockhub.api.Ctx;
import io.mockhub.api.Jsons;
import io.mockhub.api.Response;
import io.mockhub.files.BodyFiles;
import io.mockhub.project.Project;
import io.mockhub.project.ProjectService;
import io.mockhub.stub.StubService;
import java.util.List;

public final class FilesController implements ApiController {
  private final ProjectService projects;
  private final StubService stubs;
  private final BodyFiles files;

  public FilesController(ProjectService projects, StubService stubs, BodyFiles files) {
    this.projects = projects;
    this.stubs = stubs;
    this.files = files;
  }

  @Override
  public void register(ApiRouter r) {
    r.get("/projects/{id}/files", ctx -> {
      Project project = projects.get(ctx.path("id"));
      List<StubMapping> projectStubs = stubs.stubsOf(project.id());
      ArrayNode out = Jsons.MAPPER.createArrayNode();
      for (BodyFiles.Info f : files.list(project.id())) {
        ObjectNode n = Jsons.MAPPER.valueToTree(f);
        n.put("usedBy", projectStubs.stream().filter(s -> f.path().equals(s.getResponse().getBodyFileName())).count());
        out.add(n);
      }
      return out;
    });
    r.post("/projects/{id}/files", ctx -> {
      Project project = projects.get(ctx.path("id"));
      byte[] content = ctx.bytes();
      if (content == null || content.length == 0) throw ApiException.badRequest("The file is empty");
      return Response.json(201, Jsons.MAPPER.valueToTree(files.save(project.id(), name(ctx), content)));
    });
    r.get("/projects/{id}/files/content", ctx -> {
      Project project = projects.get(ctx.path("id"));
      String name = name(ctx);
      return Response.download(name, files.contentType(name), files.read(project.id(), name));
    });
    r.delete("/projects/{id}/files", ctx -> {
      Project project = projects.get(ctx.path("id"));
      if (!files.delete(project.id(), name(ctx))) throw ApiException.notFound("File " + name(ctx));
      return null;
    });
  }

  private static String name(Ctx ctx) {
    String name = ctx.query("name");
    if (name == null || name.isBlank()) throw ApiException.badRequest("Add ?name=<file name>");
    return name;
  }
}
