package io.mockhub.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.tomakehurst.wiremock.common.Urls;
import com.github.tomakehurst.wiremock.extension.Parameters;
import com.github.tomakehurst.wiremock.http.Request;
import com.github.tomakehurst.wiremock.matching.MatchResult;
import com.github.tomakehurst.wiremock.matching.RequestMatcherExtension;
import io.mockhub.project.Project;
import java.util.Optional;
import java.util.function.Function;

public final class ProjectScopeMatcher extends RequestMatcherExtension {
  public static final String NAME = "mockhub-project-scope";
  public static final String PROJECT_ID = "projectId";

  private final Function<String, Optional<Project>> projectForPath;

  public ProjectScopeMatcher(Function<String, Optional<Project>> projectForPath) {
    this.projectForPath = projectForPath;
  }

  @Override
  public String getName() {
    return NAME;
  }

  @Override
  public MatchResult match(Request request, Parameters parameters) {
    String expected = parameters.getString(PROJECT_ID, "");
    String segment = RuntimeFilter.firstSegment(Urls.getPath(request.getUrl()));
    return MatchResult.of(projectForPath.apply(segment).map(Project::id).filter(expected::equals).isPresent());
  }

  public static void scope(ObjectNode request, String projectId) {
    request.putObject("customMatcher").put("name", NAME).putObject("parameters").put(PROJECT_ID, projectId);
  }

  public static void retarget(ObjectNode stub, String projectId) {
    JsonNode matcher = stub.path("request").path("customMatcher");
    if (matcher instanceof ObjectNode m && NAME.equals(m.path("name").asText())) {
      m.putObject("parameters").put(PROJECT_ID, projectId);
    }
  }

  public static void strip(ObjectNode stub) {
    if (stub.get("request") instanceof ObjectNode request && NAME.equals(request.path("customMatcher").path("name").asText())) {
      request.remove("customMatcher");
    }
  }
}
