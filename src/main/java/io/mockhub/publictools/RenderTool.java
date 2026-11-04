package io.mockhub.publictools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.tomakehurst.wiremock.http.ResponseDefinition;
import io.mockhub.api.Jsons;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class RenderTool implements PublicTool {
  static final int MAX_REQUEST = TemplatePlayground.MAX_TEMPLATE + TemplatePlayground.MAX_BODY + 16 * 1024;

  private final TemplatePlayground playground;
  private final PublicLimits limits;

  public RenderTool(TemplatePlayground playground, PublicLimits limits) {
    this.playground = playground;
    this.limits = limits;
  }

  @Override
  public Optional<ResponseDefinition> handle(PublicRequest request) {
    if (!request.path().equals("/render")) return Optional.empty();
    if (!request.method().equals("POST")) return Optional.of(PublicResponses.error(405, "Send a POST with the template and a sample request"));
    Optional<Long> wait = limits.take(request.clientIp(), PublicLimits.Kind.WORK);
    if (wait.isPresent()) return Optional.of(PublicResponses.tooMany(wait.get(), "renders"));
    if (request.bodySize() > MAX_REQUEST) return Optional.of(PublicResponses.error(413, "Keep the template under 8 KB and the sample body under 64 KB"));
    JsonNode body = PublicJson.parse(request.body());
    if (body == null || !body.isObject()) {
      return Optional.of(PublicResponses.error(400, "Send JSON like {\"template\": \"Hello {{request.query.name}}\", \"request\": {\"url\": \"/?name=Ada\"}}"));
    }
    return Optional.of(render(body));
  }

  private ResponseDefinition render(JsonNode body) {
    JsonNode sample = body.path("request");
    Map<String, String> headers = new LinkedHashMap<>();
    sample.path("headers").properties().forEach(e -> headers.put(e.getKey(), e.getValue().isValueNode() ? e.getValue().asText() : e.getValue().toString()));
    JsonNode rawBody = sample.path("body");
    String requestBody = rawBody.isMissingNode() || rawBody.isNull() ? "" : rawBody.isTextual() ? rawBody.asText() : Jsons.write(rawBody);
    TemplatePlayground.Result result = playground.render(body.path("template").asText(""),
        new TemplatePlayground.Sample(sample.path("method").asText("GET"), sample.path("url").asText("/"), headers, requestBody));
    ObjectNode out = Jsons.object();
    if (result.output() != null) out.put("output", result.output());
    if (result.status() != null) {
      out.put("status", result.status());
      out.put("validationError", result.error());
      return PublicResponses.json(200, out);
    }
    if (result.error() != null) {
      out.put("error", result.error());
      return PublicResponses.json(422, out);
    }
    return PublicResponses.json(200, out);
  }
}
