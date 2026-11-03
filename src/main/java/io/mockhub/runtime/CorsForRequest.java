package io.mockhub.runtime;

import com.github.tomakehurst.wiremock.common.Urls;
import com.github.tomakehurst.wiremock.http.Request;
import io.mockhub.project.Project;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

public final class CorsForRequest {
  private final Function<String, Optional<Project>> projectForPath;
  private final CorsHeaders cors;

  public CorsForRequest(Function<String, Optional<Project>> projectForPath, CorsHeaders cors) {
    this.projectForPath = projectForPath;
    this.cors = cors;
  }

  public Map<String, String> headers(Request request) {
    String origin = request.getHeader("Origin");
    if (origin == null) return Map.of();
    String segment = RuntimeFilter.firstSegment(Urls.getPath(request.getUrl()));
    RuntimeSettings settings = RuntimeSettings.of(projectForPath.apply(segment).map(Project::runtime).orElse(null));
    return cors.forResponse(cors.rulesFor(settings), origin);
  }
}
