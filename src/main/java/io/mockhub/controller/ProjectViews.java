package io.mockhub.controller;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.tomakehurst.wiremock.stubbing.StubMapping;
import io.mockhub.api.Ctx;
import io.mockhub.api.Jsons;
import io.mockhub.journal.RequestLogService;
import io.mockhub.project.Project;
import io.mockhub.stub.StubMetadata;
import io.mockhub.stub.StubService;
import java.util.List;

public final class ProjectViews {

  private final StubService stubs;
  private final RequestLogService requestLog;
  private final BaseUrls urls;

  public ProjectViews(StubService stubs, RequestLogService requestLog, BaseUrls urls) {
    this.stubs = stubs;
    this.requestLog = requestLog;
    this.urls = urls;
  }

  public ObjectNode view(Ctx ctx, Project p) {
    ObjectNode node = Jsons.MAPPER.valueToTree(p);
    List<StubMapping> list = stubs.stubsOf(p.id());
    node.put("basePath", p.basePath());
    node.put("mockUrl", urls.mock(ctx, p));
    node.put("stubCount", list.size());
    node.put("soapStubCount", list.stream().filter(StubMetadata::isSoap).count());
    node.put("requestCount", requestLog.eventsOf(p).size());
    return node;
  }
}
