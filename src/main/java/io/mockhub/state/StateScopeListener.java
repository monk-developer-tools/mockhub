package io.mockhub.state;

import com.github.tomakehurst.wiremock.common.Urls;
import com.github.tomakehurst.wiremock.extension.Parameters;
import com.github.tomakehurst.wiremock.extension.ServeEventListener;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import io.mockhub.project.Project;
import io.mockhub.runtime.RuntimeFilter;
import java.util.Optional;
import java.util.function.Function;

public final class StateScopeListener implements ServeEventListener {
  private final Function<String, Optional<Project>> projectForPath;
  private final StateScope scope;

  public StateScopeListener(Function<String, Optional<Project>> projectForPath, StateScope scope) {
    this.projectForPath = projectForPath;
    this.scope = scope;
  }

  @Override
  public String getName() {
    return "mockhub-state-scope";
  }

  @Override
  public boolean applyGlobally() {
    return true;
  }

  @Override
  public void beforeMatch(ServeEvent event, Parameters parameters) {
    String segment = RuntimeFilter.firstSegment(Urls.getPath(event.getRequest().getUrl()));
    scope.enter(projectForPath.apply(segment).map(Project::id).orElse(null));
  }

  @Override
  public void afterComplete(ServeEvent event, Parameters parameters) {
    scope.leave();
  }
}
