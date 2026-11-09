package io.mockhub.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.mockhub.api.ApiException;
import io.mockhub.api.ApiRouter;
import io.mockhub.api.Jsons;
import io.mockhub.api.Response;
import io.mockhub.publictools.TemplatePlayground;
import java.util.LinkedHashMap;
import java.util.Map;

public final class TemplateController implements ApiController {
  private final TemplatePlayground playground;

  public TemplateController(TemplatePlayground playground) {
    this.playground = playground;
  }

  @Override
  public void register(ApiRouter r) {
    r.post("/templates/render", ctx -> {
      JsonNode body = ctx.json();
      if (body == null || !body.isObject()) throw ApiException.badRequest("Send {\"template\": \"...\", \"request\": {\"method\", \"url\", \"headers\", \"body\"}}");
      String template = body.path("template").asText("");
      if (template.length() > TemplatePlayground.MAX_TEMPLATE) throw new ApiException(413, "Keep the template under 8 KB");
      JsonNode sample = body.path("request");
      Map<String, String> headers = new LinkedHashMap<>();
      sample.path("headers").properties().forEach(e -> headers.put(e.getKey(), e.getValue().isValueNode() ? e.getValue().asText() : e.getValue().toString()));
      JsonNode raw = sample.path("body");
      String requestBody = raw.isMissingNode() || raw.isNull() ? "" : raw.isTextual() ? raw.asText() : Jsons.write(raw);
      if (requestBody.length() > TemplatePlayground.MAX_BODY) throw new ApiException(413, "Keep the sample body under 64 KB");
      TemplatePlayground.Result result = playground.render(template,
          new TemplatePlayground.Sample(sample.path("method").asText("GET"), sample.path("url").asText("/"), headers, requestBody));
      ObjectNode out = Jsons.object();
      if (result.output() != null) out.put("output", result.output());
      if (result.status() != null) return out.put("status", result.status()).put("validationError", result.error());
      if (result.error() != null) return Response.json(422, out.put("error", result.error()));
      return out;
    });
  }
}
