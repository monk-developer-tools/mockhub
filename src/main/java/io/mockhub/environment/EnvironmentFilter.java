package io.mockhub.environment;

import com.github.tomakehurst.wiremock.common.Urls;
import com.github.tomakehurst.wiremock.extension.requestfilter.RequestFilterAction;
import com.github.tomakehurst.wiremock.extension.requestfilter.StubRequestFilterV2;
import com.github.tomakehurst.wiremock.http.Request;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import io.mockhub.project.Project;
import io.mockhub.runtime.RuntimeFilter;
import java.util.Optional;
import java.util.function.Function;

public final class EnvironmentFilter implements StubRequestFilterV2 {
  private final Function<String, Optional<Project>> projectForPath;
  private final EnvironmentService environments;
  private final EnvironmentScope scope;

  public EnvironmentFilter(Function<String, Optional<Project>> projectForPath, EnvironmentService environments, EnvironmentScope scope) {
    this.projectForPath = projectForPath;
    this.environments = environments;
    this.scope = scope;
  }

  @Override
  public String getName() {
    return "mockhub-environment-filter";
  }

  @Override
  public RequestFilterAction filter(Request request, ServeEvent serveEvent) {
    Optional<Environment> chosen = projectForPath.apply(RuntimeFilter.firstSegment(Urls.getPath(request.getUrl())))
        .map(p -> environments.forProject(p.id()))
        .flatMap(e -> e.effective(requested(request)));
    scope.enter(chosen.orElse(null));
    if (chosen.isEmpty()) return RequestFilterAction.continueWith(request);
    return RequestFilterAction.continueWith(new EnvironmentRequest(request, chosen.get().name()));
  }

  private static String requested(Request request) {
    com.github.tomakehurst.wiremock.http.HttpHeader header = request.header(ProjectEnvironments.HEADER);
    return header != null && header.isPresent() ? header.firstValue() : null;
  }
}
