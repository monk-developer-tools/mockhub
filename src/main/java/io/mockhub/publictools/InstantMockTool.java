package io.mockhub.publictools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.tomakehurst.wiremock.http.ResponseDefinition;
import io.mockhub.api.ApiException;
import io.mockhub.api.Jsons;
import io.mockhub.graphql.ExampleOperations;
import io.mockhub.graphql.GraphQlException;
import io.mockhub.graphql.GraphQlTransformer;
import io.mockhub.grpc.GrpcCatalog;
import io.mockhub.history.HistoryEntry;
import io.mockhub.history.HistoryQuery;
import io.mockhub.history.HistoryStore;
import io.mockhub.importer.ImporterRegistry;
import io.mockhub.importer.SpecFetcher;
import io.mockhub.playground.Header;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class InstantMockTool implements PublicTool {
  public static final int MAX_REQUEST = 256 * 1024;
  public static final int RECENT = 50;
  public static final int MAX_CREATE = 600 * 1024;
  private static final Pattern MOCK = Pattern.compile("/mocks/(m-[a-z0-9]{10})(/stubs(?:/([A-Za-z0-9-]{1,64}))?|/requests|/graphql|/grpc)?");
  private static final Set<String> SPECS = Set.of("openapi", "wsdl", "graphql");

  private final InstantMocks mocks;
  private final HistoryStore history;
  private final PublicLimits limits;
  private final ImporterRegistry importers;
  private final InstantExamples examples;
  private final SpecFetcher fetcher;
  private final ExampleOperations operations;
  private final GrpcCatalog grpc;

  public InstantMockTool(InstantMocks mocks, HistoryStore history, PublicLimits limits, ImporterRegistry importers, InstantExamples examples,
      SpecFetcher fetcher, ExampleOperations operations, GrpcCatalog grpc) {
    this.mocks = mocks;
    this.history = history;
    this.limits = limits;
    this.importers = importers;
    this.examples = examples;
    this.fetcher = fetcher;
    this.operations = operations;
    this.grpc = grpc;
  }

  @Override
  public Optional<ResponseDefinition> handle(PublicRequest request) {
    if (request.path().equals("/mocks/examples")) {
      return Optional.of(request.method().equals("GET") ? exampleList() : PublicResponses.error(405, "Send a GET to list the examples"));
    }
    if (request.path().equals("/mocks")) {
      return Optional.of(request.method().equals("POST") ? create(request) : PublicResponses.error(405, "Send a POST to create an instant mock"));
    }
    Matcher m = MOCK.matcher(request.path());
    if (!m.matches()) return Optional.empty();
    if (request.bodySize() > MAX_REQUEST) return Optional.of(PublicResponses.error(413, "Keep requests under 256 KB"));
    String token = Optional.ofNullable(request.header("X-Mock-Token")).orElse(request.firstQuery("token"));
    Optional<InstantMocks.Entry> entry = mocks.owned(m.group(1), token);
    if (entry.isEmpty()) {
      return Optional.of(PublicResponses.error(404, "No instant mock " + m.group(1) + " with that key. Mocks are deleted 7 days after their last change."));
    }
    try {
      return Optional.of(route(entry.get(), m.group(2), m.group(3), request));
    } catch (InstantStubRules.Refused e) {
      return Optional.of(PublicResponses.error(400, e.getMessage()));
    } catch (ApiException e) {
      return Optional.of(PublicResponses.error(e.status, e.getMessage()));
    } catch (RuntimeException e) {
      return Optional.of(PublicResponses.error(400, "That stub isn't valid: " + e.getMessage()));
    }
  }

  private ResponseDefinition create(PublicRequest request) {
    Optional<Long> wait = limits.take(request.clientIp(), PublicLimits.Kind.CREATE);
    if (wait.isPresent()) return PublicResponses.tooMany(wait.get(), "new mocks");
    if (request.bodySize() > MAX_CREATE) return PublicResponses.error(413, "Keep requests under 600 KB");
    try {
      InstantMocks.Created created = mocks.create(source(PublicJson.parse(request.body())));
      ObjectNode out = Jsons.object()
          .put("slug", created.slug())
          .put("token", created.token())
          .put("baseUrl", "/" + created.slug());
      out.set("limits", limitsNode());
      ArrayNode skipped = out.putArray("skipped");
      created.skipped().forEach(skipped::add);
      return PublicResponses.json(201, out);
    } catch (InstantStubRules.Refused e) {
      return PublicResponses.error(400, e.getMessage());
    } catch (InstantMocks.Full e) {
      return PublicResponses.error(503, e.getMessage());
    } catch (ApiException e) {
      return PublicResponses.error(e.status, e.getMessage());
    } catch (RuntimeException e) {
      return PublicResponses.error(400, "A starter stub isn't valid: " + e.getMessage());
    }
  }

  private InstantSource source(JsonNode body) {
    if (body == null || !body.isObject()) return InstantSources.stubs(InstantStarter.stubs());
    if (body.path("stubs").isArray()) {
      List<JsonNode> stubs = new ArrayList<>();
      body.get("stubs").forEach(stubs::add);
      return InstantSources.stubs(stubs);
    }
    String from = body.path("from").asText("");
    if (SPECS.contains(from)) return InstantSources.imported(importers.get(from), specText(body));
    return switch (from) {
      case "" -> InstantSources.stubs(InstantStarter.stubs());
      case "example" -> examples.source(body.path("id").asText(""));
      case "records" -> InstantSources.records(body.path("resource").asText(null), body.get("records"));
      case "json" -> InstantSources.json(body.get("data"), body.path("resource").asText(null));
      case "grpc" -> InstantGrpc.source(grpc, body.path("text").asText(null), body.path("fileName").asText(null));
      default -> throw new InstantStubRules.Refused("Start from openapi, wsdl, graphql, grpc, json, example or records");
    };
  }

  private String specText(JsonNode body) {
    String url = body.path("url").asText("").trim();
    if (url.isEmpty()) return body.path("text").asText(null);
    if (!body.path("text").asText("").isBlank()) throw new InstantStubRules.Refused("Send the text or a URL, not both");
    return fetcher.fetch(url, InstantSources.MAX_SPEC);
  }

  private ResponseDefinition graphqlOperations(InstantMocks.Entry entry) {
    ObjectNode out = Jsons.object();
    ArrayNode endpoints = out.putArray("endpoints");
    for (JsonNode stub : mocks.stubsOf(entry)) {
      JsonNode sdl = stub.path("response").path("transformerParameters").path(GraphQlTransformer.SCHEMA);
      if (!sdl.isTextual()) continue;
      ObjectNode endpoint = endpoints.addObject().put("name", stub.path("name").asText("GraphQL")).put("path", stub.path("request").path("urlPath").asText());
      ArrayNode list = endpoint.putArray("operations");
      try {
        for (ExampleOperations.Operation o : operations.list(sdl.asText())) {
          ObjectNode op = list.addObject().put("kind", o.kind()).put("field", o.field()).put("operationName", o.operationName())
              .put("description", o.description()).put("query", o.query());
          op.set("variables", Jsons.MAPPER.valueToTree(o.variables()));
        }
      } catch (GraphQlException e) {
        endpoint.put("error", e.getMessage());
      }
    }
    return PublicResponses.json(200, out);
  }

  private ResponseDefinition exampleList() {
    ObjectNode out = Jsons.object();
    ArrayNode list = out.putArray("examples");
    examples.list().forEach(e -> list.addObject().put("id", e.id()).put("name", e.name()).put("description", e.description()).put("stubs", e.stubs()));
    return PublicResponses.json(200, out);
  }

  private ResponseDefinition route(InstantMocks.Entry entry, String part, String stubId, PublicRequest request) {
    String method = request.method();
    if (part == null) {
      if (method.equals("GET")) return PublicResponses.json(200, describe(entry));
      if (method.equals("DELETE")) {
        mocks.delete(entry);
        return PublicResponses.json(200, Jsons.object().put("deleted", true));
      }
    } else if (part.equals("/requests") && method.equals("GET")) {
      return PublicResponses.json(200, recent(entry));
    } else if (part.equals("/graphql") && method.equals("GET")) {
      return graphqlOperations(entry);
    } else if (part.equals("/grpc") && method.equals("GET")) {
      return PublicResponses.json(200, InstantGrpc.describe(grpc, entry));
    } else if (stubId == null && method.equals("POST")) {
      return PublicResponses.json(201, mocks.addStub(entry, stubBody(request)));
    } else if (stubId != null && method.equals("PUT")) {
      return PublicResponses.json(200, mocks.updateStub(entry, stubId, stubBody(request)));
    } else if (stubId != null && method.equals("DELETE")) {
      mocks.deleteStub(entry, stubId);
      return PublicResponses.json(200, Jsons.object().put("deleted", true));
    }
    return PublicResponses.error(405, "Use GET or DELETE on the mock, POST to /stubs, PUT or DELETE on /stubs/{id}, GET /requests, GET /graphql or GET /grpc");
  }

  private static JsonNode stubBody(PublicRequest request) {
    JsonNode body = PublicJson.parse(request.body());
    if (body == null) throw new InstantStubRules.Refused("Send the stub as JSON");
    return body;
  }

  private ObjectNode describe(InstantMocks.Entry entry) {
    ObjectNode out = Jsons.object()
        .put("slug", entry.slug())
        .put("baseUrl", "/" + entry.slug())
        .put("createdAt", entry.createdAt())
        .put("expiresAt", mocks.expires(entry).toString())
        .put("requestsToday", mocks.requestsToday(entry.projectId()));
    out.set("limits", limitsNode());
    ArrayNode list = out.putArray("stubs");
    mocks.stubsOf(entry).forEach(list::add);
    return out;
  }

  private ObjectNode recent(InstantMocks.Entry entry) {
    ObjectNode out = Jsons.object();
    ArrayNode items = out.putArray("requests");
    for (HistoryEntry e : history.search(entry.projectId(), HistoryQuery.recent(RECENT), true).items()) {
      ObjectNode item = items.addObject()
          .put("id", e.id()).put("at", e.at()).put("method", e.method()).put("url", e.url().replaceFirst("^/" + entry.slug(), ""))
          .put("status", e.status()).put("matched", e.matched()).put("stubName", e.stubName()).put("durationMs", e.durationMs())
          .put("requestBody", e.requestBody()).put("responseBody", e.responseBody());
      item.set("requestHeaders", headers(e.requestHeaders()));
      item.set("responseHeaders", headers(e.responseHeaders()));
    }
    return out;
  }

  private static ArrayNode headers(List<Header> list) {
    ArrayNode out = Jsons.MAPPER.createArrayNode();
    list.stream().filter(h -> !h.name().toLowerCase().startsWith("x-forwarded-") && !h.name().equalsIgnoreCase("x-real-ip"))
        .forEach(h -> out.addObject().put("name", h.name()).put("value", h.value()));
    return out;
  }

  private static ObjectNode limitsNode() {
    return Jsons.object()
        .put("stubs", InstantMocks.MAX_STUBS)
        .put("requestsPerDay", InstantMocks.DAILY_REQUESTS)
        .put("stubBytes", InstantStubRules.MAX_STUB_BYTES)
        .put("idleDays", InstantMocks.IDLE.toDays());
  }
}
