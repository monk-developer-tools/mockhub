package io.mockhub.history;

import com.github.tomakehurst.wiremock.extension.Parameters;
import com.github.tomakehurst.wiremock.extension.ServeEventListener;
import com.github.tomakehurst.wiremock.http.HttpHeaders;
import com.github.tomakehurst.wiremock.http.LoggedResponse;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import com.github.tomakehurst.wiremock.stubbing.StubMapping;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import io.mockhub.playground.Header;
import io.mockhub.project.Project;
import io.mockhub.runtime.RuntimeFilter;
import io.mockhub.stub.StubMetadata;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

public final class HistoryRecorder implements ServeEventListener {
  public static final String NAME = "mockhub-history";

  private final Function<String, Optional<Project>> projectForPath;
  private final HistoryStore store;
  private final TrafficCounters traffic;

  public HistoryRecorder(Function<String, Optional<Project>> projectForPath, HistoryStore store, TrafficCounters traffic) {
    this.projectForPath = projectForPath;
    this.store = store;
    this.traffic = traffic;
  }

  @Override
  public String getName() {
    return NAME;
  }

  @Override
  public boolean applyGlobally() {
    return true;
  }

  @Override
  public void afterComplete(ServeEvent event, Parameters parameters) {
    LoggedRequest request = event.getRequest();
    if (request == null || request.getUrl() == null || request.getUrl().startsWith("/__admin")) return;
    String url = request.getUrl();
    int query = url.indexOf('?');
    Optional<Project> project = projectForPath.apply(RuntimeFilter.firstSegment(query < 0 ? url : url.substring(0, query)));
    if (project.isEmpty()) return;
    HistoryEntry entry = entry(event, project.get().id());
    traffic.record(entry.projectId(), entry.at(), entry.matched());
    store.record(entry);
  }

  static HistoryEntry entry(ServeEvent event, String projectId) {
    LoggedRequest request = event.getRequest();
    LoggedResponse response = event.getResponse();
    StubMapping stub = event.getWasMatched() ? event.getStubMapping() : null;
    boolean fallback = stub != null && StubMetadata.isManaged(stub);
    byte[] requestBody = request.getBody() == null ? new byte[0] : request.getBody();
    byte[] responseBody = response == null || response.getBody() == null ? new byte[0] : response.getBody();
    Integer total = event.getTiming() == null ? null : event.getTiming().getTotalTime();
    return new HistoryEntry(
        event.getId() == null ? java.util.UUID.randomUUID().toString() : event.getId().toString(),
        request.getLoggedDate() == null ? System.currentTimeMillis() : request.getLoggedDate().getTime(),
        projectId,
        request.getMethod().getName(),
        request.getUrl(),
        request.getAbsoluteUrl(),
        request.getClientIp(),
        headers(request.getHeaders()),
        HistoryEntry.cap(text(requestBody)),
        requestBody.length > HistoryEntry.MAX_BODY,
        response == null ? 0 : response.getStatus(),
        response == null ? List.of() : headers(response.getHeaders()),
        HistoryEntry.cap(text(responseBody)),
        responseBody.length > HistoryEntry.MAX_BODY,
        stub != null && !fallback,
        stub == null || stub.getId() == null ? null : stub.getId().toString(),
        stub == null ? null : fallback ? "Default response" : stub.getName(),
        total == null ? 0 : total);
  }

  private static List<Header> headers(HttpHeaders headers) {
    List<Header> out = new ArrayList<>();
    if (headers == null) return out;
    headers.all().forEach(h -> h.values().forEach(v -> out.add(new Header(h.key(), v))));
    return out;
  }

  static String text(byte[] body) {
    if (body.length == 0) return "";
    String s = new String(body, StandardCharsets.UTF_8);
    return s.indexOf('\u0000') >= 0 || s.indexOf('�') >= 0 ? "[binary content, " + body.length + " bytes]" : s;
  }
}
