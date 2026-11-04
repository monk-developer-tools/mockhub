package io.mockhub.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.mockhub.api.Jsons;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PickerCatalogueIntegrationTest {

  private static final String API = "/__admin/mockhub/api";
  private static final Path CATALOGUE = Path.of("ui", "src", "model", "dynamicValues.json");
  private static final String TOKEN = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZGEiLCJzY29wZSI6InJlYWQifQ.c2lnbmF0dXJl";
  private static final String BODY = """
      {"id":"0b9a3c56-8f7e-4d6b-9a2c-1e2f3a4b5c6d","type":"gold","items":[{"id":1,"name":"Book"},{"id":2,"name":"Pen"}],
       "customer":{"name":"Ada"},"password":"secret","amount":120,"age":30,"name":"  ada lovelace  ","username":"ada",
       "text":"A long description of the order","card":"4111111111111111","email":"ada@example.com","code":"ABC"}""";
  private static final Map<String, String> HEADERS = Map.of(
      "X-Request-Id", "req-1", "X-Region", "EU", "Authorization", "Bearer " + TOKEN, "Cookie", "session=abc");

  @TempDir
  Path data;
  private MockHubServer server;
  private ApiClient api;
  private String projectId;

  private static final Map<String, String[]> INPUTS = Map.of(
      "xml", new String[] {"<order><id>42</id></order>", "application/xml"},
      "soap", new String[] {"<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\"><soap:Body><GetOrder><id>42</id></GetOrder></soap:Body></soap:Envelope>", "text/xml"},
      "form", new String[] {"email=ada%40example.com&name=Ada", "application/x-www-form-urlencoded"});

  private record Entry(String group, String scope, String label, String expression, String input) {}

  @BeforeEach
  void start() {
    server = MockHubServer.start(data);
    api = server.api();
    projectId = api.post(API + "/projects", "{\"name\":\"Catalogue\",\"slug\":\"cat\"}").json().get("id").asText();
    api.post(API + "/projects/" + projectId + "/stubs", "{\"request\":{\"method\":\"POST\",\"urlPath\":\"/receiver\"},\"response\":{\"status\":204}}");
  }

  @AfterEach
  void stop() {
    server.close();
  }

  private static List<Entry> entries() throws IOException {
    List<Entry> out = new ArrayList<>();
    for (JsonNode group : Jsons.MAPPER.readTree(Files.readString(CATALOGUE)).get("groups")) {
      for (JsonNode item : group.get("items")) {
        out.add(new Entry(group.get("name").asText(), group.path("scope").asText(""), item.get("label").asText(), item.get("expression").asText(), item.path("input").asText("")));
      }
    }
    return out;
  }

  private void stub(ObjectNode stub) {
    ApiClient.Result created = api.post(API + "/projects/" + projectId + "/stubs", stub.toString());
    assertThat(created.status()).as(created.body()).isEqualTo(201);
  }

  private String inResponse(int index, Entry entry) {
    ObjectNode stub = Jsons.object();
    stub.putObject("request").put("method", "POST").put("urlPath", "/r" + index);
    ObjectNode response = stub.putObject("response").put("status", 200).put("body", entry.expression().replace("$R.", "request."));
    response.putArray("transformers").add("response-template");
    stub(stub);
    String[] input = INPUTS.getOrDefault(entry.input(), new String[] {BODY, "application/json"});
    ApiClient.Result result = api.send("POST", "/cat/r" + index + "?page=2&q=books&status=paid&code=ab1", input[0], input[1], HEADERS);
    return result.status() == 200 ? result.body() : "HTTP " + result.status() + ": " + result.body();
  }

  private String inCallout(int index, Entry entry) {
    ObjectNode stub = Jsons.object();
    stub.putObject("request").put("method", "POST").put("urlPath", "/c" + index);
    ObjectNode response = stub.putObject("response").put("status", 200);
    response.set("jsonBody", Jsons.object().put("id", 7).put("status", "PAID"));
    response.putObject("headers").put("Content-Type", "application/json");
    response.putArray("transformers").add("mockhub-sync-callout");
    response.putObject("transformerParameters").putObject("callout")
        .put("method", "POST").put("url", server.baseUrl() + "/cat/receiver")
        .put("body", entry.expression().replace("$R.", "originalRequest."));
    stub(stub);
    api.send("POST", "/cat/c" + index + "?page=2&q=books&status=paid", BODY, "application/json", HEADERS);
    JsonNode sent = api.get(API + "/projects/" + projectId + "/callouts").json().get(0);
    return sent.path("error").isMissingNode() || sent.path("error").isNull() ? sent.at("/request/body").asText() : "callout error: " + sent.path("error").asText();
  }

  @Test
  void everyPickerEntryRendersWithoutAnError() throws IOException {
    // Given
    List<Entry> entries = entries();
    List<String> failures = new ArrayList<>();

    // When
    for (int i = 0; i < entries.size(); i++) {
      Entry entry = entries.get(i);
      String rendered = entry.scope().equals("callout") ? inCallout(i, entry) : inResponse(i, entry);
      if (rendered.startsWith("HTTP ") || rendered.startsWith("callout error") || rendered.contains("[ERROR") || rendered.contains("{{") || rendered.contains("can't")) {
        failures.add(entry.group() + " › " + entry.label() + " → " + rendered.replaceAll("\\s+", " ").substring(0, Math.min(200, rendered.replaceAll("\\s+", " ").length())));
      }
    }

    // Then
    assertThat(entries).hasSizeGreaterThan(100);
    assertThat(failures).isEmpty();
  }
}
