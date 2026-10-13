package io.mockhub.journal;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import io.mockhub.api.Jsons;
import io.mockhub.project.Project;
import io.mockhub.project.ProjectService;
import io.mockhub.stub.StubGateway;
import java.util.List;
import java.util.stream.Collectors;

public final class RequestLogService {
  private final StubGateway gateway;
  private final ProjectService projects;

  public RequestLogService(StubGateway gateway, ProjectService projects) {
    this.gateway = gateway;
    this.projects = projects;
  }

  public List<ServeEvent> eventsOf(Project p) {
    String base = p.basePath();
    return gateway.serveEvents().stream()
        .filter(e -> isUnder(e.getRequest().getUrl(), base))
        .collect(Collectors.toList());
  }

  public static int[] activity(List<Long> timestamps, long nowMillis, int buckets, long bucketMillis) {
    int[] counts = new int[buckets];
    long start = nowMillis - buckets * bucketMillis;
    for (long at : timestamps) {
      if (at < start || at > nowMillis) continue;
      int index = (int) Math.min(buckets - 1, (at - start) / bucketMillis);
      counts[index]++;
    }
    return counts;
  }

  public ArrayNode list(String projectId, int limit, boolean unmatchedOnly) {
    ArrayNode out = Jsons.MAPPER.createArrayNode();
    eventsOf(projects.get(projectId)).stream()
        .filter(e -> !unmatchedOnly || !e.getWasMatched())
        .limit(Math.max(1, limit))
        .forEach(e -> out.add(Jsons.fromWireMock(e)));
    return out;
  }

  public void clear(String projectId) {
    gateway.removeServeEventsUnder(projects.get(projectId).basePath());
  }

  static boolean isUnder(String url, String base) {
    return url.equals(base) || url.startsWith(base + "/") || url.startsWith(base + "?");
  }
}
