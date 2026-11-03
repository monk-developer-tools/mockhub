package io.mockhub.runtime;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.tomakehurst.wiremock.stubbing.StubMapping;
import io.mockhub.api.Jsons;
import io.mockhub.project.Project;
import io.mockhub.stub.StubMetadata;
import io.mockhub.stub.StubService;

public final class DefaultResponseStubs {
  public static final String NAME = "Default response";
  public static final int PRIORITY = 1_000_000;

  private final StubService stubs;

  public DefaultResponseStubs(StubService stubs) {
    this.stubs = stubs;
  }

  public void sync(Project project) {
    stubs.stubsOf(project.id()).stream()
        .filter(StubMetadata::isManaged)
        .map(StubMapping::getId)
        .toList()
        .forEach(id -> stubs.delete(project.id(), id.toString()));
    DefaultResponse response = RuntimeSettings.of(project.runtime()).defaultResponse();
    if (response != null) stubs.create(project.id(), stub(project, response));
  }

  static ObjectNode stub(Project project, DefaultResponse response) {
    ObjectNode stub = Jsons.object();
    stub.put("name", NAME).put("priority", PRIORITY);
    ObjectNode request = stub.putObject("request").put("method", "ANY");
    ProjectScopeMatcher.scope(request, project.id());
    ObjectNode out = stub.putObject("response").put("status", response.status());
    if (!response.headers().isEmpty()) {
      ObjectNode headers = out.putObject("headers");
      response.headers().forEach(headers::put);
    }
    if (!response.body().isEmpty()) out.put("body", response.body());
    if (response.templated()) out.putArray("transformers").add("response-template");
    StubMetadata.node(stub).put(StubMetadata.MANAGED, StubMetadata.MANAGED_DEFAULT_RESPONSE);
    return stub;
  }
}
