package io.mockhub.publictools;

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.extension.ResponseDefinitionTransformerV2;
import com.github.tomakehurst.wiremock.http.HttpHeader;
import com.github.tomakehurst.wiremock.http.HttpHeaders;
import com.github.tomakehurst.wiremock.http.Request;
import com.github.tomakehurst.wiremock.http.ResponseDefinition;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import io.mockhub.api.Jsons;
import java.util.ArrayList;
import java.util.List;

public final class SandboxTemplateTransformer implements ResponseDefinitionTransformerV2 {
  public static final String NAME = "mockhub-public-template";
  static final long BUDGET_MS = 1000;

  private final TemplatePlayground playground;

  public SandboxTemplateTransformer(TemplatePlayground playground) {
    this.playground = playground;
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
    String body = original.getBody();
    String renderedBody = null;
    if (body != null && body.contains("{{")) {
      TemplatePlayground.Result result = playground.renderFor(body, request, BUDGET_MS);
      if (result.output() == null) return failed(result);
      renderedBody = result.output();
    }
    List<HttpHeader> headers = new ArrayList<>();
    if (original.getHeaders() != null) {
      for (HttpHeader header : original.getHeaders().all()) {
        List<String> values = new ArrayList<>();
        for (String value : header.values()) {
          if (!value.contains("{{")) {
            values.add(value);
            continue;
          }
          TemplatePlayground.Result result = playground.renderFor(value, request, BUDGET_MS);
          if (result.output() == null) return failed(result);
          values.add(result.output());
        }
        headers.add(new HttpHeader(header.key(), values));
      }
    }
    ResponseDefinitionBuilder builder = ResponseDefinitionBuilder.like(original).but().withHeaders(new HttpHeaders(headers));
    if (renderedBody != null) builder.withBody(renderedBody);
    return builder.build();
  }

  private static ResponseDefinition failed(TemplatePlayground.Result result) {
    int status = result.status() == null ? 500 : result.status();
    String message = result.status() == null ? "Template error: " + result.error() : result.error();
    return new ResponseDefinitionBuilder().withStatus(status).withHeader("Content-Type", "application/json")
        .withBody(Jsons.write(Jsons.object().put("error", message).put("status", status))).build();
  }
}
