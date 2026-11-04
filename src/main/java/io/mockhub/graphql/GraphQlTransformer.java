package io.mockhub.graphql;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.extension.ResponseDefinitionTransformerV2;
import com.github.tomakehurst.wiremock.http.HttpHeader;
import com.github.tomakehurst.wiremock.http.QueryParameter;
import com.github.tomakehurst.wiremock.http.Request;
import com.github.tomakehurst.wiremock.http.ResponseDefinition;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import io.mockhub.api.Jsons;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class GraphQlTransformer implements ResponseDefinitionTransformerV2 {
  public static final String NAME = "mockhub-graphql";
  public static final String SCHEMA = "schema";
  static final int MAX_BATCH = 20;

  private final GraphQlEngine engine;

  public GraphQlTransformer(GraphQlEngine engine) {
    this.engine = engine;
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
    Object schema = original.getTransformerParameters() == null ? null : original.getTransformerParameters().get(SCHEMA);
    if (!(schema instanceof String sdl) || sdl.isBlank()) return answer(original, 500, GraphQlEngine.error("This GraphQL stub has no transformerParameters.schema"));
    Request request = event.getRequest();
    String method = request.getMethod().getName();
    if (!method.equals("GET") && !method.equals("POST")) {
      return answer(original, 405, GraphQlEngine.error("Send GraphQL requests with POST, or GET with a query parameter"));
    }
    try {
      if (method.equals("GET")) return answer(original, 200, run(sdl, param(request, "query"), param(request, "operationName"), variables(param(request, "variables"))));
      String contentType = request.contentTypeHeader().isPresent() ? request.contentTypeHeader().mimeTypePart() : "";
      if (contentType != null && contentType.toLowerCase(Locale.ROOT).contains("application/graphql")) {
        return answer(original, 200, run(sdl, request.getBodyAsString(), null, Map.of()));
      }
      JsonNode body = Jsons.MAPPER.readTree(request.getBodyAsString());
      if (body != null && body.isArray()) {
        if (body.size() > MAX_BATCH) return answer(original, 400, GraphQlEngine.error("Send at most " + MAX_BATCH + " operations in one batch"));
        List<Object> results = new ArrayList<>();
        body.forEach(item -> results.add(runOne(sdl, item)));
        return answer(original, 200, results);
      }
      if (body == null || !body.isObject()) return answer(original, 400, GraphQlEngine.error("Send a JSON body like {\"query\": \"{ ... }\", \"variables\": {}}"));
      return answer(original, 200, runOne(sdl, body));
    } catch (GraphQlException e) {
      return answer(original, e.getMessage().startsWith("The schema") ? 500 : 400, GraphQlEngine.error(e.getMessage()));
    } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
      return answer(original, 400, GraphQlEngine.error("The body isn't valid JSON: " + e.getOriginalMessage()));
    }
  }

  private Map<String, Object> runOne(String sdl, JsonNode item) {
    if (!item.isObject()) throw new GraphQlException("Each operation must be a JSON object with a query");
    Map<String, Object> variables = item.hasNonNull("variables") && item.get("variables").isObject()
        ? Jsons.MAPPER.convertValue(item.get("variables"), new TypeReference<Map<String, Object>>() {})
        : Map.of();
    return run(sdl, item.path("query").asText(null), item.path("operationName").asText(null), variables);
  }

  private Map<String, Object> run(String sdl, String query, String operationName, Map<String, Object> variables) {
    if (query == null || query.isBlank()) throw new GraphQlException("The request has no query");
    return engine.execute(sdl, query, operationName, variables);
  }

  private static Map<String, Object> variables(String text) {
    if (text == null || text.isBlank()) return Map.of();
    try {
      return Jsons.MAPPER.readValue(text, new TypeReference<Map<String, Object>>() {});
    } catch (Exception e) {
      throw new GraphQlException("The variables parameter isn't a JSON object");
    }
  }

  private static String param(Request request, String name) {
    QueryParameter p = request.queryParameter(name);
    return p == null || !p.isPresent() ? null : p.firstValue();
  }

  private static ResponseDefinition answer(ResponseDefinition original, int status, Object body) {
    ResponseDefinitionBuilder out = new ResponseDefinitionBuilder()
        .withStatus(status)
        .withFixedDelay(original.getFixedDelayMilliseconds())
        .withRandomDelay(original.getDelayDistribution());
    boolean typed = false;
    if (original.getHeaders() != null) {
      for (HttpHeader h : original.getHeaders().all()) {
        out.withHeader(h.key(), h.values().toArray(String[]::new));
        typed |= h.key().equalsIgnoreCase("Content-Type");
      }
    }
    if (!typed) out.withHeader("Content-Type", "application/json");
    return out.withBody(Jsons.MAPPER.valueToTree(body).toString()).build();
  }
}
