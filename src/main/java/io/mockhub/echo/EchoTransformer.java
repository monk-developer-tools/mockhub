package io.mockhub.echo;

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.common.Urls;
import com.github.tomakehurst.wiremock.extension.ResponseDefinitionTransformerV2;
import com.github.tomakehurst.wiremock.http.HttpHeader;
import com.github.tomakehurst.wiremock.http.Request;
import com.github.tomakehurst.wiremock.http.ResponseDefinition;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import io.mockhub.api.Jsons;
import io.mockhub.project.Project;
import io.mockhub.publictools.PublicRequest;
import io.mockhub.publictools.RequestEcho;
import io.mockhub.stub.StubMetadata;
import java.util.Optional;
import java.util.function.Function;

public final class EchoTransformer implements ResponseDefinitionTransformerV2 {
  public static final String NAME = "mockhub-echo";
  public static final int MAX_BODY = 1024 * 1024;

  private final Function<String, Optional<Project>> projects;

  public EchoTransformer(Function<String, Optional<Project>> projects) {
    this.projects = projects;
  }

  @Override
  public String getName() {
    return NAME;
  }

  @Override
  public boolean applyGlobally() {
    return false;
  }

  @Override
  public ResponseDefinition transform(ServeEvent event) {
    ResponseDefinition original = event.getResponseDefinition();
    Request request = event.getRequest();
    byte[] body = request.getBody();
    if (body != null && body.length > MAX_BODY) {
      return answer(original, 413, Jsons.write(Jsons.object().put("error", "The echo takes bodies up to 1 MB")));
    }
    String path = relativePath(Urls.getPath(request.getUrl()), basePath(event));
    return answer(original, original.getStatus() > 0 ? original.getStatus() : 200, Jsons.write(RequestEcho.describe(PublicRequest.at(request, path), path)));
  }

  private String basePath(ServeEvent event) {
    String projectId = event.getStubMapping() == null ? null : StubMetadata.projectId(event.getStubMapping());
    if (projectId == null) return "";
    return projects.apply(projectId).map(Project::basePath).orElse("");
  }

  static String relativePath(String path, String basePath) {
    if (basePath.isEmpty() || !(path.equals(basePath) || path.startsWith(basePath + "/"))) return path;
    String rest = path.substring(basePath.length());
    return rest.isEmpty() ? "/" : rest;
  }

  private static ResponseDefinition answer(ResponseDefinition original, int status, String body) {
    ResponseDefinitionBuilder out = new ResponseDefinitionBuilder()
        .withStatus(status)
        .withFixedDelay(original.getFixedDelayMilliseconds())
        .withRandomDelay(original.getDelayDistribution());
    if (original.getHeaders() != null) {
      for (HttpHeader h : original.getHeaders().all()) {
        if (!h.key().equalsIgnoreCase("Content-Type")) out.withHeader(h.key(), h.values().toArray(String[]::new));
      }
    }
    return out.withHeader("Content-Type", "application/json").withBody(body).build();
  }
}
