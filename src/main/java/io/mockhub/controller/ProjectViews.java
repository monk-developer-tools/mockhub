package io.mockhub.controller;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.tomakehurst.wiremock.stubbing.StubMapping;
import io.mockhub.api.Ctx;
import io.mockhub.api.Jsons;
import io.mockhub.journal.RequestLogService;
import io.mockhub.project.Project;
import io.mockhub.stub.StubMetadata;
import io.mockhub.stub.StubService;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

public final class ProjectViews {

  private final StubService stubs;
  private final RequestLogService requestLog;
  private final BaseUrls urls;
  private final Clock clock;

  private static final int ACTIVITY_BUCKETS = 12;
  private static final Duration ACTIVITY_BUCKET = Duration.ofMinutes(5);

  public ProjectViews(StubService stubs, RequestLogService requestLog, BaseUrls urls, Clock clock) {
    this.stubs = stubs;
    this.requestLog = requestLog;
    this.urls = urls;
    this.clock = clock;
  }

  public ObjectNode view(Ctx ctx, Project p) {
    ObjectNode node = Jsons.MAPPER.valueToTree(p);
    List<StubMapping> list = stubs.stubsOf(p.id());
    node.put("basePath", p.basePath());
    node.put("mockUrl", urls.mock(ctx, p));
    node.put("stubCount", list.size());
    node.put("soapStubCount", list.stream().filter(StubMetadata::isSoap).count());
    List<ServeEvent> events = requestLog.eventsOf(p);
    node.put("requestCount", events.size());
    node.put("unmatchedCount", events.stream().filter(e -> !e.getWasMatched()).count());
    List<Long> timestamps = events.stream().map(e -> e.getRequest().getLoggedDate().getTime()).toList();
    timestamps.stream().mapToLong(Long::longValue).max()
        .ifPresent(last -> node.put("lastRequestAt", Instant.ofEpochMilli(last).toString()));
    ArrayNode activity = node.putArray("activity");
    for (int count : RequestLogService.activity(timestamps, clock.millis(), ACTIVITY_BUCKETS, ACTIVITY_BUCKET.toMillis())) {
      activity.add(count);
    }
    return node;
  }
}
