package io.mockhub.system;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.mockhub.support.Zips;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MockHubSystemTest {

  private static final String API = "/__admin/mockhub/api";
  private static final ObjectMapper JSON = new ObjectMapper();
  private static final Map<String, String> AUTH_ENV = Map.of("MOCKHUB_USERS", "lead:s3cret:admin");
  private static final String AUTH = "Basic " + Base64.getEncoder().encodeToString("lead:s3cret".getBytes(StandardCharsets.UTF_8));

  private final HttpClient http = HttpClient.newHttpClient();

  @TempDir
  Path data;

  @Test
  void aTeamMocksRestAndSoapServicesThatSurviveARestart() throws Exception {
    String projectId;
    try (MockHubProcess hub = MockHubProcess.start(data, AUTH_ENV)) {
      // Given
      assertThat(sendAnonymously(hub, API + "/projects").statusCode()).isEqualTo(401);
      projectId = json(send(hub, "POST", API + "/projects", "{\"name\":\"Order Service\",\"slug\":\"orders\"}")).get("id").asText();

      // When
      send(hub, "POST", API + "/projects/" + projectId + "/stubs",
          "{\"name\":\"Get order\",\"request\":{\"method\":\"GET\",\"urlPathTemplate\":\"/orders/{id}\"},"
              + "\"response\":{\"status\":200,\"jsonBody\":{\"id\":\"{{request.path.id}}\",\"state\":\"SHIPPED\"},"
              + "\"transformers\":[\"response-template\"]}}");
      send(hub, "POST", API + "/projects/" + projectId + "/import/wsdl", resource("calculator.wsdl"));

      // Then
      HttpResponse<String> order = send(hub, "GET", "/orders/orders/99", null);
      assertThat(order.statusCode()).isEqualTo(200);
      assertThat(json(order).get("id").asText()).isEqualTo("99");

      HttpResponse<String> soap = send(hub, "POST", "/orders/ws/calculator",
          "<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\"><s:Body><Add xmlns=\"http://example.com/calculator\"/></s:Body></s:Envelope>");
      assertThat(soap.body()).contains("AddResponse");

      JsonNode postman = json(send(hub, "GET", API + "/projects/" + projectId + "/export/postman", null));
      assertThat(postman.at("/variable/0/value").asText()).isEqualTo(hub.baseUrl() + "/orders");
      assertThat(postman.get("item")).extracting(f -> f.get("name").asText()).contains("orders", "CalculatorService");
    }

    // When
    try (MockHubProcess restarted = MockHubProcess.start(data, AUTH_ENV)) {
      // Then
      JsonNode project = json(send(restarted, "GET", API + "/projects/" + projectId, null));
      assertThat(project.get("stubCount").asInt()).isEqualTo(5);
      assertThat(json(send(restarted, "GET", "/orders/orders/7", null)).get("state").asText()).isEqualTo("SHIPPED");
      assertThat(send(restarted, "GET", "/orders/ws/calculator?wsdl", null).body()).contains(restarted.baseUrl() + "/orders/ws/calculator");
    }
  }

  @Test
  void aSnapshotTakenBeforeARestartRestoresStubsAndFilesAfterIt() throws Exception {
    String projectId;
    String snapshotId;
    try (MockHubProcess hub = MockHubProcess.start(data, AUTH_ENV)) {
      // Given
      projectId = json(send(hub, "POST", API + "/projects", "{\"name\":\"Catalog\",\"slug\":\"catalog\"}")).get("id").asText();
      String path = json(send(hub, "POST", API + "/projects/" + projectId + "/files?name=prices.csv", "sku,price\nA-1,9.99\n")).get("path").asText();
      send(hub, "POST", API + "/projects/" + projectId + "/stubs",
          "{\"name\":\"Prices\",\"request\":{\"method\":\"GET\",\"urlPath\":\"/prices\"},\"response\":{\"status\":200,\"bodyFileName\":\"" + path + "\"}}");
      snapshotId = json(send(hub, "POST", API + "/projects/" + projectId + "/snapshots", "{\"name\":\"release 1\"}")).get("id").asText();

      // When
      String stubId = json(send(hub, "GET", API + "/projects/" + projectId + "/stubs", null)).get(0).get("id").asText();
      send(hub, "DELETE", API + "/projects/" + projectId + "/stubs/" + stubId, null);
      send(hub, "DELETE", API + "/projects/" + projectId + "/files?name=prices.csv", null);

      // Then
      assertThat(send(hub, "GET", "/catalog/prices", null).statusCode()).isEqualTo(404);
    }

    try (MockHubProcess restarted = MockHubProcess.start(data, AUTH_ENV)) {
      // When
      HttpResponse<String> restored = send(restarted, "POST", API + "/projects/" + projectId + "/snapshots/" + snapshotId + "/restore", "{}");

      // Then
      assertThat(restored.statusCode()).as(restored.body()).isEqualTo(200);
      assertThat(send(restarted, "GET", "/catalog/prices", null).body()).contains("A-1,9.99");
      assertThat(json(send(restarted, "GET", API + "/projects/" + projectId + "/snapshots", null))).hasSize(2);
    }
  }

  @Test
  void organisationsUsersAndSessionsSurviveARestart() throws Exception {
    String token;
    String orgId;
    try (MockHubProcess hub = MockHubProcess.start(data, AUTH_ENV)) {
      // Given
      orgId = json(send(hub, "POST", API + "/orgs", "{\"name\":\"Payments\"}")).get("id").asText();
      String userId = json(send(hub, "POST", API + "/users", "{\"username\":\"ana\",\"password\":\"longenough\"}")).get("id").asText();
      send(hub, "PUT", API + "/orgs/" + orgId + "/members/" + userId, "{\"role\":\"owner\"}");
      send(hub, "PUT", API + "/sso", "{\"tenantId\":\"contoso.onmicrosoft.com\",\"clientId\":\"abc\",\"clientSecret\":\"s\",\"enabled\":true}");

      // When
      token = json(sendAnonymously(hub, "POST", API + "/auth/login", "{\"username\":\"ana\",\"password\":\"longenough\"}")).get("token").asText();
      HttpResponse<String> created = sendAs(hub, "Bearer " + token, "POST", API + "/projects", "{\"name\":\"Cards\",\"orgId\":\"" + orgId + "\"}");

      // Then
      assertThat(created.statusCode()).isEqualTo(201);
    }

    try (MockHubProcess restarted = MockHubProcess.start(data, AUTH_ENV)) {
      // When
      JsonNode me = json(sendAs(restarted, "Bearer " + token, "GET", API + "/auth/me", null));
      JsonNode info = json(sendAnonymously(restarted, "GET", API + "/auth/info", null));

      // Then
      assertThat(me.get("username").asText()).isEqualTo("ana");
      assertThat(me.at("/orgs/0/id").asText()).isEqualTo(orgId);
      assertThat(me.at("/orgs/0/role").asText()).isEqualTo("manager");
      assertThat(json(sendAs(restarted, "Bearer " + token, "GET", API + "/projects", null))).hasSize(1);
      assertThat(info.at("/sso/enabled").asBoolean()).isTrue();
      HttpResponse<String> start = sendAnonymously(restarted, "GET", API + "/auth/sso/azure/start", null);
      assertThat(start.statusCode()).isEqualTo(302);
      assertThat(start.headers().firstValue("Location").orElse(""))
          .startsWith("https://login.microsoftonline.com/contoso.onmicrosoft.com/oauth2/v2.0/authorize?client_id=abc");
    }
  }

  @Test
  void anImportedWireMockFolderAnswersAtTheRootAfterARestart() throws Exception {
    Map<String, String> folder = new LinkedHashMap<>();
    folder.put("wiremock/mappings/catalog.json",
        "{\"request\":{\"method\":\"GET\",\"urlPath\":\"/catalog/v2/items\"},\"response\":{\"status\":200,\"bodyFileName\":\"catalog/items.json\"}}");
    folder.put("wiremock/__files/catalog/items.json", "[{\"sku\":\"A-1\"}]");
    try (MockHubProcess hub = MockHubProcess.start(data, AUTH_ENV)) {
      // Given
      String id = json(send(hub, "POST", API + "/projects", "{\"name\":\"Catalog\",\"root\":true}")).get("id").asText();

      // When
      HttpResponse<String> imported = http.send(HttpRequest.newBuilder(URI.create(hub.baseUrl() + API + "/projects/" + id + "/import/wiremock-zip"))
          .POST(HttpRequest.BodyPublishers.ofByteArray(Zips.of(folder))).header("Authorization", AUTH).header("Content-Type", "application/zip").build(),
          HttpResponse.BodyHandlers.ofString());

      // Then
      assertThat(json(imported).get("imported").asInt()).isEqualTo(1);
      assertThat(send(hub, "GET", "/catalog/v2/items", null).body()).contains("A-1");
    }

    // When
    try (MockHubProcess restarted = MockHubProcess.start(data, AUTH_ENV)) {
      // Then
      assertThat(send(restarted, "GET", "/catalog/v2/items", null).body()).contains("A-1");
    }
  }

  @Test
  void aWireMockRootFolderServesAsIsAndItsStubsCanBeMovedIntoAProjectAndEdited() throws Exception {
    // Given
    Files.createDirectories(data.resolve("mappings/acme/orders"));
    Files.createDirectories(data.resolve("__files/acme/orders"));
    Files.writeString(data.resolve("mappings/acme/orders/all.json"), """
        {"mappings":[
          {"request":{"method":"GET","urlPath":"/orders/1"},"response":{"status":200,"bodyFileName":"./acme/orders/1.json"}},
          {"request":{"method":"GET","urlPathPattern":"/orders/[0-9]+/status"},"response":{"status":200,"body":"{{request.pathSegments.[1]}} shipped","transformers":["response-template"]}}]}""");
    Files.writeString(data.resolve("__files/acme/orders/1.json"), "{\"id\":1}");

    try (MockHubProcess hub = MockHubProcess.start(data, AUTH_ENV)) {
      assertThat(send(hub, "GET", "/orders/1", null).body()).isEqualTo("{\"id\":1}");
      String projectId = json(send(hub, "POST", API + "/projects", "{\"name\":\"Legacy\",\"root\":true}")).get("id").asText();

      // When
      for (JsonNode stub : json(send(hub, "GET", API + "/stubs/unassigned", null))) {
        assertThat(send(hub, "POST", API + "/projects/" + projectId + "/adopt/" + stub.get("id").asText() + "?prefix=false", null).statusCode()).isEqualTo(200);
      }
      JsonNode first = null;
      for (JsonNode stub : json(send(hub, "GET", API + "/projects/" + projectId + "/stubs", null))) if (stub.at("/response/bodyFileName").isTextual()) first = stub;
      HttpResponse<String> edited = send(hub, "PUT", API + "/projects/" + projectId + "/stubs/" + first.get("id").asText(),
          "{\"request\":{\"method\":\"GET\",\"urlPath\":\"/orders/1\"},\"response\":{\"status\":201,\"bodyFileName\":\"./acme/orders/1.json\"}}");

      // Then
      assertThat(edited.statusCode()).isEqualTo(200);
      assertThat(json(send(hub, "GET", API + "/stubs/unassigned", null))).isEmpty();
      assertThat(Files.exists(data.resolve("mappings/acme/orders/all.json"))).isFalse();
    }

    // When
    try (MockHubProcess restarted = MockHubProcess.start(data, AUTH_ENV)) {
      // Then
      HttpResponse<String> order = send(restarted, "GET", "/orders/1", null);
      assertThat(order.statusCode()).isEqualTo(201);
      assertThat(order.body()).isEqualTo("{\"id\":1}");
      assertThat(send(restarted, "GET", "/orders/7/status", null).body()).isEqualTo("7 shipped");
      assertThat(json(send(restarted, "GET", API + "/stubs/unassigned", null))).isEmpty();
    }
  }

  @Test
  void crudRecordsAnswerFilteredSortedAndPagedQueriesAfterARestart() throws Exception {
    String query = "/shop/orders?total%5Bgte%5D=20&status=paid&status=shipped&sort=-total&page=1&size=2";
    try (MockHubProcess hub = MockHubProcess.start(data, AUTH_ENV)) {
      // Given
      String id = json(send(hub, "POST", API + "/projects", "{\"name\":\"Shop\",\"slug\":\"shop\"}")).get("id").asText();
      send(hub, "POST", API + "/projects/" + id + "/crud", "{\"resource\":\"orders\",\"idType\":\"number\"}");

      // When
      for (String order : new String[] {"{\"total\":12,\"status\":\"paid\"}", "{\"total\":40,\"status\":\"paid\"}",
          "{\"total\":75,\"status\":\"shipped\"}", "{\"total\":99,\"status\":\"cancelled\"}", "{\"total\":25,\"status\":\"shipped\"}"}) {
        send(hub, "POST", "/shop/orders", order);
      }

      // Then
      HttpResponse<String> page = send(hub, "GET", query, null);
      assertThat(json(page).findValuesAsText("total")).containsExactly("75", "40");
      assertThat(page.headers().firstValue("X-Total-Count")).contains("3");
    }

    // When
    try (MockHubProcess restarted = MockHubProcess.start(data, AUTH_ENV)) {
      // Then
      assertThat(json(send(restarted, "GET", query.replace("page=1", "page=2"), null)).findValuesAsText("total")).containsExactly("25");
    }
  }

  @Test
  void stubsWithVeryLongUrlsAreSavedAndSurviveARestart() throws Exception {
    String path = "/customers/search/" + "where-custom-fields-email-equals-someone-at-example-com-and-".repeat(5);
    try (MockHubProcess hub = MockHubProcess.start(data, AUTH_ENV)) {
      // Given
      String id = json(send(hub, "POST", API + "/projects", "{\"name\":\"Legacy\",\"root\":true}")).get("id").asText();

      // When
      HttpResponse<String> created = send(hub, "POST", API + "/projects/" + id + "/stubs",
          "{\"request\":{\"method\":\"GET\",\"url\":\"" + path + "\"},\"response\":{\"status\":200,\"body\":\"found\"}}");

      // Then
      assertThat(created.statusCode()).isEqualTo(201);
      assertThat(send(hub, "GET", path, null).body()).isEqualTo("found");
    }

    // When
    try (MockHubProcess restarted = MockHubProcess.start(data, AUTH_ENV)) {
      // Then
      assertThat(send(restarted, "GET", path, null).body()).isEqualTo("found");
    }
  }

  @Test
  void auditEntriesSurviveARestartAndAreWrittenToStdout() throws Exception {
    try (MockHubProcess hub = MockHubProcess.start(data, AUTH_ENV)) {
      // Given
      String id = json(send(hub, "POST", API + "/projects", "{\"name\":\"Orders\"}")).get("id").asText();
      String stubId = json(send(hub, "POST", API + "/projects/" + id + "/stubs",
          "{\"name\":\"List orders\",\"request\":{\"method\":\"GET\",\"url\":\"/orders\"},\"response\":{\"status\":200}}")).get("id").asText();

      // When
      send(hub, "DELETE", API + "/projects/" + id + "/stubs/" + stubId, null);
      http.send(HttpRequest.newBuilder(URI.create(hub.baseUrl() + API + "/auth/login")).header("Content-Type", "application/json")
          .POST(HttpRequest.BodyPublishers.ofString("{\"username\":\"lead\",\"password\":\"wrong\"}")).build(), HttpResponse.BodyHandlers.ofString());

      // Then
      String stdout = hub.stdout();
      assertThat(stdout).contains("INFO audit - lead: Deleted stub").contains("Deleted stub “List orders”").contains("Failed sign-in for 'lead'");
    }

    // When
    try (MockHubProcess restarted = MockHubProcess.start(data, AUTH_ENV)) {
      JsonNode entries = json(send(restarted, "GET", API + "/audit", null)).get("entries");

      // Then
      assertThat(entries).hasSize(3);
      assertThat(entries.get(0).get("action").asText()).isEqualTo("deleted");
      assertThat(entries.get(0).get("targetName").asText()).isEqualTo("List orders");
      assertThat(entries.get(2).get("action").asText()).isEqualTo("created");
    }
  }

  @Test
  void stubsCallBackWaitForPartnersAndKeepWorkingAfterARestart() throws Exception {
    String projectId;
    try (MockHubProcess hub = MockHubProcess.start(data, AUTH_ENV)) {
      // Given
      projectId = json(send(hub, "POST", API + "/projects", "{\"name\":\"Payments\",\"slug\":\"pay\"}")).get("id").asText();
      send(hub, "POST", API + "/projects/" + projectId + "/stubs",
          "{\"request\":{\"method\":\"POST\",\"urlPath\":\"/issuer\"},\"response\":{\"status\":201,\"jsonBody\":{\"approved\":true}}}");
      send(hub, "POST", API + "/projects/" + projectId + "/stubs",
          "{\"request\":{\"method\":\"POST\",\"urlPath\":\"/check\"},\"response\":{\"status\":200,\"transformers\":[\"mockhub-sync-callout\"],"
              + "\"transformerParameters\":{\"callout\":{\"url\":\"{{originalRequest.baseUrl}}/pay/issuer\",\"forward\":true}}},"
              + "\"serveEventListeners\":[{\"name\":\"mockhub-callout\",\"parameters\":{\"method\":\"POST\",\"url\":\"/pay/issuer\",\"body\":\"{{originalResponse.status}}\"}}]}");

      // When
      HttpResponse<String> check = send(hub, "POST", "/pay/check", "{\"card\":\"4242\"}");

      // Then
      assertThat(check.statusCode()).isEqualTo(201);
      assertThat(json(check).get("approved").asBoolean()).isTrue();
      JsonNode log = waitForCallouts(hub, projectId, 2);
      assertThat(log).extracting(e -> e.get("mode").asText()).containsExactlyInAnyOrder("sync", "async");
      assertThat(log).filteredOn(e -> e.get("mode").asText().equals("async"))
          .singleElement().satisfies(e -> assertThat(e.at("/request/body").asText()).isEqualTo("201"));
    }

    // When
    try (MockHubProcess restarted = MockHubProcess.start(data, AUTH_ENV)) {
      // Then
      assertThat(send(restarted, "POST", "/pay/check", "{}").statusCode()).isEqualTo(201);
      assertThat(waitForCallouts(restarted, projectId, 4)).hasSize(4);
      JsonNode history = json(send(restarted, "GET", API + "/projects/" + projectId + "/history?method=POST&q=/pay/check", null));
      assertThat(history.get("total").asInt()).isEqualTo(2);
    }
  }

  private JsonNode waitForCallouts(MockHubProcess hub, String projectId, int count) throws Exception {
    long deadline = System.currentTimeMillis() + 10_000;
    JsonNode log = json(send(hub, "GET", API + "/projects/" + projectId + "/callouts", null));
    while (log.size() < count && System.currentTimeMillis() < deadline) {
      Thread.sleep(100);
      log = json(send(hub, "GET", API + "/projects/" + projectId + "/callouts", null));
    }
    return log;
  }

  @Test
  void servesTheUiEntryPoint() throws Exception {
    try (MockHubProcess hub = MockHubProcess.start(data, Map.of())) {
      // When
      HttpResponse<String> redirect = send(hub, "GET", "/__admin/mockhub", null);
      HttpResponse<String> ui = send(hub, "GET", "/__admin/mockhub/ui/projects/abc", null);

      // Then
      assertThat(redirect.statusCode()).isEqualTo(302);
      assertThat(redirect.headers().firstValue("Location")).hasValue("/__admin/mockhub/ui/");
      assertThat(ui.statusCode()).isEqualTo(200);
      assertThat(ui.headers().firstValue("Content-Type").orElse("")).startsWith("text/html");
    }
  }

  @Test
  void publicToolsWorkWithoutLoginWhenTurnedOn() throws Exception {
    try (MockHubProcess hub = MockHubProcess.start(data, Map.of("MOCKHUB_PUBLIC_TOOLS", "true"))) {
      // When
      HttpResponse<String> echo = sendAnonymously(hub, "POST", "/__public/echo/hello?x=1", "{\"a\":1}");
      HttpResponse<String> render = sendAnonymously(hub, "POST", "/__public/render",
          "{\"template\":\"{{request.query.who}} {{#each (range 1 3)}}{{this}}{{/each}}\",\"request\":{\"url\":\"/?who=team\"}}");

      // Then
      assertThat(echo.statusCode()).isEqualTo(200);
      assertThat(json(echo).get("path").asText()).isEqualTo("/hello");
      assertThat(render.statusCode()).isEqualTo(200);
      assertThat(json(render).get("output").asText()).isEqualTo("team 123");
    }
  }

  private HttpResponse<String> send(MockHubProcess hub, String method, String path, String body) throws IOException, InterruptedException {
    HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(hub.baseUrl() + path))
        .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body))
        .header("Authorization", AUTH);
    return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
  }

  private HttpResponse<String> sendAnonymously(MockHubProcess hub, String path) throws IOException, InterruptedException {
    return http.send(HttpRequest.newBuilder(URI.create(hub.baseUrl() + path)).build(), HttpResponse.BodyHandlers.ofString());
  }

  private HttpResponse<String> sendAnonymously(MockHubProcess hub, String method, String path, String body) throws IOException, InterruptedException {
    return sendAs(hub, null, method, path, body);
  }

  private HttpResponse<String> sendAs(MockHubProcess hub, String authorization, String method, String path, String body)
      throws IOException, InterruptedException {
    HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(hub.baseUrl() + path))
        .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
    if (authorization != null) b.header("Authorization", authorization);
    return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
  }

  private static JsonNode json(HttpResponse<String> response) throws IOException {
    return JSON.readTree(response.body());
  }

  private static String resource(String name) throws IOException {
    try (InputStream in = MockHubSystemTest.class.getResourceAsStream("/" + name)) {
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
  }
}
