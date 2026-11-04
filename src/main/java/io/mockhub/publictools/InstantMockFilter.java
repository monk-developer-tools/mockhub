package io.mockhub.publictools;

import com.github.tomakehurst.wiremock.common.Urls;
import com.github.tomakehurst.wiremock.extension.requestfilter.RequestFilterAction;
import com.github.tomakehurst.wiremock.extension.requestfilter.StubRequestFilterV2;
import com.github.tomakehurst.wiremock.http.Request;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import io.mockhub.project.Project;
import java.util.Optional;
import java.util.function.Function;

public final class InstantMockFilter implements StubRequestFilterV2 {
  public static final int MAX_BODY = 64 * 1024;

  private final Function<String, Optional<Project>> projectForPath;
  private final InstantMocks mocks;

  public InstantMockFilter(Function<String, Optional<Project>> projectForPath, InstantMocks mocks) {
    this.projectForPath = projectForPath;
    this.mocks = mocks;
  }

  @Override
  public String getName() {
    return "mockhub-instant-mocks";
  }

  @Override
  public RequestFilterAction filter(Request request, ServeEvent serveEvent) {
    String path = Urls.getPath(request.getUrl());
    if (!path.startsWith("/" + InstantMocks.SLUG_PREFIX)) return RequestFilterAction.continueWith(request);
    String first = path.substring(1).split("/", 2)[0];
    Optional<Project> project = projectForPath.apply(first).filter(mocks::isInstant);
    if (project.isEmpty()) return RequestFilterAction.continueWith(request);
    byte[] body = request.getBody();
    if (body != null && body.length > MAX_BODY) {
      return RequestFilterAction.stopWith(PublicResponses.error(413, "Instant mocks take request bodies up to 64 KB"));
    }
    if (!mocks.takeRequest(project.get().id())) {
      return RequestFilterAction.stopWith(PublicResponses.error(429, "This instant mock has had " + InstantMocks.DAILY_REQUESTS
          + " requests today. It answers again tomorrow (UTC)."));
    }
    return RequestFilterAction.continueWith(request);
  }
}
