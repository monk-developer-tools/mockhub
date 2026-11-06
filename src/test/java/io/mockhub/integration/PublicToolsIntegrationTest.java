package io.mockhub.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import io.mockhub.api.Jsons;
import io.mockhub.support.Fixtures;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PublicToolsIntegrationTest {

  private static final Map<String, String> ON = Map.of("MOCKHUB_PUBLIC_TOOLS", "true");

  @TempDir
  Path data;
  private MockHubServer server;

  @AfterEach
  void stop() {
    if (server != null) server.close();
  }

  @Test
  void staysOffUnlessTurnedOn() {
    // Given
    server = MockHubServer.start(data);

    // When
    ApiClient.Result result = server.api().get("/__public/echo");

    // Then
    assertThat(result.status()).isEqualTo(404);
    assertThat(result.body()).doesNotContain("\"method\"");
  }

  @Test
  void echoesRequestsWithoutLogin() {
    // Given
    server = MockHubServer.start(data, ON);

    // When
    ApiClient.Result result = server.api().send("PUT", "/__public/echo/things?x=1", "{\"n\":2}", "application/json", Map.of("X-Demo", "yes"));

    // Then
    assertThat(result.status()).isEqualTo(200);
    JsonNode body = result.json();
    assertThat(body.get("method").asText()).isEqualTo("PUT");
    assertThat(body.get("path").asText()).isEqualTo("/things");
    assertThat(body.at("/headers/X-Demo").asText()).isEqualTo("yes");
    assertThat(body.at("/json/n").asInt()).isEqualTo(2);
    assertThat(result.header("Access-Control-Allow-Origin")).isEqualTo("*");
  }

  @Test
  void catchesWebhooksInABinAndShowsThemToItsOwner() {
    // Given
    server = MockHubServer.start(data, ON);
    ApiClient api = server.api();
    JsonNode bin = api.post("/__public/bins", "").json();
    String id = bin.get("id").asText();
    String token = bin.get("token").asText();
    api.send("PUT", "/__public/inspect/" + id + "/response", "{\"status\":202,\"contentType\":\"text/plain\",\"body\":\"got it\"}",
        "application/json", Map.of("X-Bin-Token", token));

    // When
    ApiClient.Result hook = api.post("/__public/bins/" + id + "/payments?ref=9", "{\"paid\":true}");
    ApiClient.Result owner = api.send("GET", "/__public/inspect/" + id, null, null, Map.of("X-Bin-Token", token));
    ApiClient.Result stranger = api.get("/__public/inspect/" + id);

    // Then
    assertThat(hook.status()).isEqualTo(202);
    assertThat(hook.body()).isEqualTo("got it");
    JsonNode captured = owner.json().at("/requests/0");
    assertThat(captured.get("method").asText()).isEqualTo("POST");
    assertThat(captured.get("path").asText()).isEqualTo("/payments");
    assertThat(captured.get("query").asText()).isEqualTo("ref=9");
    assertThat(captured.get("body").asText()).isEqualTo("{\"paid\":true}");
    assertThat(stranger.status()).isEqualTo(404);
  }

  @Test
  void answersBinWebhooksFromRulesAndShowsTheAnswerBesideEachRequest() {
    // Given
    server = MockHubServer.start(data, ON);
    ApiClient api = server.api();
    JsonNode bin = api.post("/__public/bins", "").json();
    String id = bin.get("id").asText();
    Map<String, String> owner = Map.of("X-Bin-Token", bin.get("token").asText());
    String rules = """
        {"rulesEnabled": true, "rules": [
          {"name": "failed payments", "method": "POST",
           "conditions": [{"target": "json", "key": "$.type", "op": "equals", "value": "payment.failed"}],
           "answers": [{"status": 402, "delayMs": 300, "body": "{\\"id\\": \\"{{jsonPath request.body '$.id'}}\\"}", "template": true}]}
        ]}
        """;
    ApiClient.Result saved = api.send("PUT", "/__public/inspect/" + id + "/rules", rules, "application/json", owner);

    // When
    long started = System.currentTimeMillis();
    ApiClient.Result failed = api.post("/__public/bins/" + id + "/stripe", "{\"type\":\"payment.failed\",\"id\":\"pi_7\"}");
    long took = System.currentTimeMillis() - started;
    ApiClient.Result paid = api.post("/__public/bins/" + id + "/stripe", "{\"type\":\"payment.paid\"}");
    JsonNode listing = api.send("GET", "/__public/inspect/" + id, null, null, owner).json();

    // Then
    assertThat(saved.status()).isEqualTo(200);
    assertThat(failed.status()).isEqualTo(402);
    assertThat(failed.json().get("id").asText()).isEqualTo("pi_7");
    assertThat(took).isGreaterThanOrEqualTo(300);
    assertThat(paid.status()).isEqualTo(200);
    assertThat(listing.get("total").asLong()).isEqualTo(2);
    assertThat(listing.at("/requests/0/event").asText()).isEqualTo("payment.paid");
    assertThat(listing.at("/requests/0/outcome/by").asText()).isEqualTo("default");
    assertThat(listing.at("/requests/1/event").asText()).isEqualTo("payment.failed");
    assertThat(listing.at("/requests/1/outcome/by").asText()).isEqualTo("rule");
    assertThat(listing.at("/requests/1/outcome/rule").asText()).isEqualTo("failed payments");
    assertThat(listing.at("/requests/1/outcome/status").asInt()).isEqualTo(402);
  }

  @Test
  void aBinRuleSendsAnUploadedFileAndLogsItAsBinary() {
    // Given
    server = MockHubServer.start(data, ON);
    ApiClient api = server.api();
    JsonNode bin = api.post("/__public/bins", "").json();
    String id = bin.get("id").asText();
    Map<String, String> owner = Map.of("X-Bin-Token", bin.get("token").asText());
    String rules = """
        {"rulesEnabled": true, "rules": [{"name": "Logo", "description": "Serves the logo", "method": "GET",
          "answers": [{"status": 200, "body": "iVBORw0KGgo=", "fileName": "logo.png", "description": "The real file"}]}]}
        """;
    api.send("PUT", "/__public/inspect/" + id + "/rules", rules, "application/json", owner);

    // When
    ApiClient.Result logo = api.send("GET", "/__public/bins/" + id + "/logo.png", null, null);
    JsonNode saved = api.send("GET", "/__public/inspect/" + id + "/settings", null, null, owner).json();
    JsonNode log = api.send("GET", "/__public/inspect/" + id, null, null, owner).json();

    // Then
    assertThat(logo.bytes()).containsExactly((byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n');
    assertThat(logo.contentType()).isEqualTo("image/png");
    assertThat(saved.at("/rules/0/description").asText()).isEqualTo("Serves the logo");
    assertThat(saved.at("/rules/0/answers/0/fileName").asText()).isEqualTo("logo.png");
    assertThat(saved.at("/rules/0/answers/0/description").asText()).isEqualTo("The real file");
    assertThat(log.at("/requests/0/outcome/body").asText()).isEqualTo("(8 bytes of binary data)");
  }

  @Test
  void binRulesMatchNewMethodsPathPatternsFormsAndSoapActions() {
    // Given
    server = MockHubServer.start(data, ON);
    ApiClient api = server.api();
    JsonNode bin = api.post("/__public/bins", "").json();
    String id = bin.get("id").asText();
    Map<String, String> owner = Map.of("X-Bin-Token", bin.get("token").asText());
    String rules = """
        {"rulesEnabled": true, "rules": [
          {"name": "Search", "method": "QUERY", "conditions": [{"target": "path", "op": "template", "value": "/shops/:shop/search"}],
           "answers": [{"status": 200, "template": true, "body": "{{pathParams.shop}}"}]},
          {"name": "Trace", "method": "TRACE", "answers": [{"status": 204}]},
          {"name": "Paid form", "method": "POST", "conditions": [{"target": "form", "key": "status", "op": "equals", "value": "paid"}],
           "answers": [{"status": 202}]},
          {"name": "Refund", "method": "POST", "conditions": [{"target": "soap_action", "op": "equals", "value": "urn:Refund"},
                                                              {"target": "xml", "key": "//amount", "op": "not_equals", "value": "0"}],
           "answers": [{"status": 200, "body": "<ok/>"}]}]}
        """;
    ApiClient.Result saved = api.send("PUT", "/__public/inspect/" + id + "/rules", rules, "application/json", owner);
    String base = "/__public/bins/" + id;

    // When
    ApiClient.Result query = api.send("QUERY", base + "/shops/berlin/search", "{\"q\":\"tea\"}", "application/json");
    ApiClient.Result trace = api.send("TRACE", base + "/anything", null, null);
    ApiClient.Result form = api.send("POST", base + "/hook", "status=paid&id=7", "application/x-www-form-urlencoded");
    ApiClient.Result soap = api.send("POST", base + "/soap", "<Envelope><Body><Refund><amount>12</amount></Refund></Body></Envelope>", "text/xml",
        Map.of("SOAPAction", "\"urn:Refund\""));
    JsonNode log = api.send("GET", "/__public/inspect/" + id, null, null, owner).json();

    // Then
    assertThat(saved.status()).isEqualTo(200);
    assertThat(query.status()).isEqualTo(200);
    assertThat(query.body()).isEqualTo("berlin");
    assertThat(trace.status()).isEqualTo(204);
    assertThat(form.status()).isEqualTo(202);
    assertThat(soap.body()).isEqualTo("<ok/>");
    assertThat(log.at("/requests/3/method").asText()).isEqualTo("QUERY");
    assertThat(log.at("/requests/3/outcome/rule").asText()).isEqualTo("Search");
  }

  @Test
  void aBinsDefaultAnswerCanBeBuiltFromEachRequest() {
    // Given
    server = MockHubServer.start(data, ON);
    ApiClient api = server.api();
    JsonNode bin = api.post("/__public/bins", "").json();
    String id = bin.get("id").asText();
    Map<String, String> owner = Map.of("X-Bin-Token", bin.get("token").asText());
    String reply = "{\"status\": 202, \"contentType\": \"application/json\", \"template\": true, "
        + "\"body\": \"{\\\"order\\\": \\\"{{jsonPath request.body '$.id'}}\\\", \\\"currency\\\": \\\"{{request.query.currency}}\\\"}\"}";
    ApiClient.Result saved = api.send("PUT", "/__public/inspect/" + id + "/response", reply, "application/json", owner);

    // When
    ApiClient.Result hook = api.post("/__public/bins/" + id + "/orders?currency=EUR", "{\"id\":\"ord_3\"}");
    JsonNode settings = api.send("GET", "/__public/inspect/" + id + "/settings", null, null, owner).json();

    // Then
    assertThat(saved.status()).isEqualTo(200);
    assertThat(hook.status()).isEqualTo(202);
    assertThat(hook.json().get("order").asText()).isEqualTo("ord_3");
    assertThat(hook.json().get("currency").asText()).isEqualTo("EUR");
    assertThat(settings.at("/reply/template").asBoolean()).isTrue();
  }

  @Test
  void aViewOnlyLinkShowsRequestsButCannotChangeTheBin() {
    // Given
    server = MockHubServer.start(data, ON);
    ApiClient api = server.api();
    JsonNode bin = api.post("/__public/bins", "").json();
    String id = bin.get("id").asText();
    Map<String, String> owner = Map.of("X-Bin-Token", bin.get("token").asText());
    String key = api.send("GET", "/__public/inspect/" + id + "/settings", null, null, owner).json().get("viewKey").asText();
    Map<String, String> viewer = Map.of("X-Bin-Token", key);
    api.post("/__public/bins/" + id + "/orders", "{\"id\":\"ord_5\"}");

    // When
    ApiClient.Result seen = api.send("GET", "/__public/inspect/" + id, null, null, viewer);
    ApiClient.Result settings = api.send("GET", "/__public/inspect/" + id + "/settings", null, null, viewer);
    ApiClient.Result rules = api.send("PUT", "/__public/inspect/" + id + "/rules", "{\"rules\": []}", "application/json", viewer);
    ApiClient.Result cleared = api.send("DELETE", "/__public/inspect/" + id, null, null, viewer);

    // Then
    assertThat(seen.status()).isEqualTo(200);
    assertThat(seen.json().at("/requests/0/path").asText()).isEqualTo("/orders");
    assertThat(settings.status()).isEqualTo(403);
    assertThat(rules.status()).isEqualTo(403);
    assertThat(rules.json().get("error").asText()).contains("view-only");
    assertThat(cleared.status()).isEqualTo(403);
    assertThat(api.send("GET", "/__public/inspect/" + id, null, null, owner).json().get("requests")).hasSize(1);
  }

  @Test
  void keepsEveryBinRequestAndPagesBackThroughThem() {
    // Given
    server = MockHubServer.start(data, ON);
    ApiClient api = server.api();
    JsonNode bin = api.post("/__public/bins", "").json();
    String id = bin.get("id").asText();
    Map<String, String> owner = Map.of("X-Bin-Token", bin.get("token").asText());
    for (int i = 1; i <= 130; i++) api.post("/__public/bins/" + id + "/n", "{\"n\":" + i + "}");

    // When
    JsonNode first = api.send("GET", "/__public/inspect/" + id, null, null, owner).json();
    JsonNode older = api.send("GET", "/__public/inspect/" + id + "?before=31", null, null, owner).json();

    // Then
    assertThat(first.get("total").asLong()).isEqualTo(130);
    assertThat(first.get("requests").size()).isEqualTo(100);
    assertThat(first.at("/requests/0/seq").asLong()).isEqualTo(130);
    assertThat(older.get("requests").size()).isEqualTo(30);
    assertThat(older.at("/requests/29/seq").asLong()).isEqualTo(1);
  }

  @Test
  void routesBinWebhooksOnlyToPublicAddressesAndReportsTheSettings() {
    // Given
    server = MockHubServer.start(data, ON);
    ApiClient api = server.api();
    JsonNode bin = api.post("/__public/bins", "").json();
    String id = bin.get("id").asText();
    Map<String, String> owner = Map.of("X-Bin-Token", bin.get("token").asText());
    api.send("PUT", "/__public/inspect/" + id + "/routing", "{\"mode\":\"forward\",\"target\":\"http://127.0.0.1:9\"}", "application/json", owner);

    // When
    ApiClient.Result hook = api.post("/__public/bins/" + id + "/hook", "{}");
    JsonNode settings = api.send("GET", "/__public/inspect/" + id + "/settings", null, null, owner).json();
    ApiClient.Result draft = api.send("POST", "/__public/inspect/" + id + "/draft", "{\"description\":\"fail every refund\"}", "application/json", owner);

    // Then
    assertThat(hook.status()).isEqualTo(502);
    assertThat(hook.body()).contains("private or local");
    assertThat(settings.at("/routing/mode").asText()).isEqualTo("forward");
    assertThat(settings.at("/tunnel/name").asText()).isEqualTo("bin-" + id);
    assertThat(settings.at("/tunnel/online").asBoolean()).isFalse();
    assertThat(settings.get("ai").asBoolean()).isFalse();
    assertThat(draft.status()).isEqualTo(409);
  }

  @Test
  void rendersTemplatesAgainstASampleRequest() {
    // Given
    server = MockHubServer.start(data, ON);
    String body = "{\"template\":\"Hi {{request.query.name}}, {{jsonPath request.body '$.n'}} {{fake 'Name.firstName'}}\","
        + "\"request\":{\"method\":\"POST\",\"url\":\"/x?name=Ada\",\"body\":{\"n\":3}}}";

    // When
    ApiClient.Result ok = server.api().post("/__public/render", body);
    ApiClient.Result refused = server.api().post("/__public/render", "{\"template\":\"{{systemValue key='HOME'}}\"}");

    // Then
    assertThat(ok.status()).isEqualTo(200);
    assertThat(ok.json().get("output").asText()).startsWith("Hi Ada, 3 ").hasSizeGreaterThan("Hi Ada, 3 ".length());
    assertThat(refused.status()).isEqualTo(422);
    assertThat(refused.json().get("error").asText()).contains("turned off");
  }

  @Test
  void generatesFakeData() {
    // Given
    server = MockHubServer.start(data, ON);

    // When
    ApiClient.Result result = server.api().post("/__public/fake",
        "{\"count\":2,\"fields\":[{\"name\":\"id\",\"expression\":\"uuid\"},{\"name\":\"user.email\",\"expression\":\"Internet.emailAddress\"}]}");

    // Then
    assertThat(result.status()).isEqualTo(200);
    assertThat(result.json().get("records")).hasSize(2);
    assertThat(result.json().at("/records/0/user/email").asText()).contains("@");
  }

  @Test
  void answersBrowserPreflights() {
    // Given
    server = MockHubServer.start(data, ON);

    // When
    ApiClient.Result result = server.api().send("OPTIONS", "/__public/render", null, null,
        Map.of("Origin", "https://example.com", "Access-Control-Request-Method", "POST"));

    // Then
    assertThat(result.status()).isEqualTo(204);
    assertThat(result.header("Access-Control-Allow-Methods")).contains("POST");
  }

  @Test
  void makesAnInstantMockWithoutAnAccountThatAnswersRightAway() {
    // Given
    server = MockHubServer.start(data, ON);
    ApiClient api = server.api();
    JsonNode created = api.post("/__public/mocks", "").json();
    String base = created.get("baseUrl").asText();
    Map<String, String> key = Map.of("X-Mock-Token", created.get("token").asText());

    // When
    ApiClient.Result hello = api.get(base + "/hello");
    ApiClient.Result user = api.get(base + "/users/7");
    ApiClient.Result added = api.send("POST", "/__public/mocks/" + created.get("slug").asText() + "/stubs",
        "{\"name\":\"Pay\",\"request\":{\"method\":\"POST\",\"urlPath\":\"/payments\"},\"response\":{\"status\":201,\"jsonBody\":{\"id\":\"p1\"}}}",
        "application/json", key);
    ApiClient.Result paid = api.post(base + "/payments", "{}");
    JsonNode mock = api.send("GET", "/__public/mocks/" + created.get("slug").asText(), null, null, key).json();
    JsonNode requests = api.send("GET", "/__public/mocks/" + created.get("slug").asText() + "/requests", null, null, key).json();

    // Then
    assertThat(base).matches("/m-[a-z0-9]{10}");
    assertThat(hello.json().get("message").asText()).isEqualTo("Hello from your instant mock");
    assertThat(user.json().get("id").asText()).isEqualTo("7");
    assertThat(user.json().get("email").asText()).contains("@");
    assertThat(added.status()).isEqualTo(201);
    assertThat(paid.status()).isEqualTo(201);
    assertThat(mock.get("stubs")).hasSize(3);
    assertThat(mock.get("stubs").findValuesAsText("name")).containsExactlyInAnyOrder("Say hello", "Get a user", "Pay");
    assertThat(mock.toString()).contains("\"response-template\"").doesNotContain("mockhub-public-template");
    assertThat(requests.get("requests").size()).isGreaterThanOrEqualTo(1);
  }

  @Test
  void managesAnInstantMockOnlyWithItsKey() {
    // Given
    server = MockHubServer.start(data, ON);
    ApiClient api = server.api();
    JsonNode created = api.post("/__public/mocks", "").json();
    String path = "/__public/mocks/" + created.get("slug").asText();

    // When
    ApiClient.Result noKey = api.get(path);
    ApiClient.Result wrongKey = api.send("DELETE", path, null, null, Map.of("X-Mock-Token", "guess"));
    ApiClient.Result proxy = api.send("POST", path + "/stubs",
        "{\"request\":{\"url\":\"/x\"},\"response\":{\"proxyBaseUrl\":\"http://169.254.169.254\"}}", "application/json",
        Map.of("X-Mock-Token", created.get("token").asText()));

    // Then
    assertThat(noKey.status()).isEqualTo(404);
    assertThat(wrongKey.status()).isEqualTo(404);
    assertThat(proxy.status()).isEqualTo(400);
    assertThat(proxy.json().get("error").asText()).contains("proxyBaseUrl");
  }

  @Test
  void rendersInstantMockTemplatesInsideTheSandbox() {
    // Given
    server = MockHubServer.start(data, ON);
    ApiClient api = server.api();
    JsonNode created = api.post("/__public/mocks", "{\"stubs\":[{\"request\":{\"urlPath\":\"/bomb\"},\"response\":{\"status\":200,"
        + "\"body\":\"{{#each (range 1 100)}}{{#each (range 1 100)}}x{{/each}}{{/each}}{{systemValue key='user.home'}}\","
        + "\"transformers\":[\"response-template\"]}}]}").json();

    // When
    ApiClient.Result bomb = api.get(created.get("baseUrl").asText() + "/bomb");

    // Then
    assertThat(bomb.status()).isEqualTo(500);
    assertThat(bomb.json().get("error").asText()).contains("Loops in the public playground run up to");
    awaitHistory(api, created);
  }

  private static void awaitHistory(ApiClient api, JsonNode created) {
    Map<String, String> key = Map.of("X-Mock-Token", created.get("token").asText());
    String path = "/__public/mocks/" + created.get("slug").asText() + "/requests";
    long deadline = System.currentTimeMillis() + 5_000;
    while (api.send("GET", path, null, null, key).json().get("requests").isEmpty() && System.currentTimeMillis() < deadline) {
      Thread.onSpinWait();
    }
  }

  @Test
  void hidesInstantMocksFromServerViewersAndTheWireMockAdminApi() {
    // Given
    server = MockHubServer.start(data, Map.of("MOCKHUB_PUBLIC_TOOLS", "true", "MOCKHUB_USERS", "admin:pw:admin,demo:demo:viewer"));
    ApiClient api = server.api();
    JsonNode created = api.post("/__public/mocks", "").json();
    ApiClient viewer = server.api().as("demo", "demo");
    ApiClient admin = server.api().as("admin", "pw");

    // When
    JsonNode seenByViewer = viewer.get("/__admin/mockhub/api/projects").json();
    JsonNode seenByAdmin = admin.get("/__admin/mockhub/api/projects").json();
    ApiClient.Result nativeApi = viewer.get("/__admin/mappings");

    // Then
    assertThat(seenByViewer.findValuesAsText("slug")).doesNotContain(created.get("slug").asText());
    assertThat(seenByAdmin.findValuesAsText("slug")).contains(created.get("slug").asText());
    assertThat(nativeApi.status()).isEqualTo(403);
  }

  @Test
  void startsInstantMocksFromASpecAWsdlRecordsOrAnExample() {
    // Given
    server = MockHubServer.start(data, ON);
    ApiClient api = server.api();

    // When
    JsonNode fromSpec = api.post("/__public/mocks", Jsons.object().put("from", "openapi").put("text", Fixtures.resource("petstore.yaml")).toString()).json();
    JsonNode fromWsdl = api.post("/__public/mocks", Jsons.object().put("from", "wsdl").put("text", Fixtures.resource("calculator.wsdl")).toString()).json();
    JsonNode fromRecords = api.post("/__public/mocks",
        "{\"from\":\"records\",\"resource\":\"users\",\"records\":[{\"id\":1,\"name\":\"Ada\"},{\"id\":2,\"name\":\"Linus\"}]}").json();
    JsonNode examples = api.get("/__public/mocks/examples").json().get("examples");

    // Then
    assertThat(api.get(fromSpec.get("baseUrl").asText() + "/pets").status()).isEqualTo(200);
    String wsdlPath = stubPaths(api, fromWsdl).stream().findFirst().orElseThrow();
    ApiClient.Result wsdl = api.get(fromWsdl.get("baseUrl").asText() + wsdlPath + "?wsdl");
    assertThat(wsdl.status()).isEqualTo(200);
    assertThat(wsdl.body()).contains("definitions");
    String users = fromRecords.get("baseUrl").asText() + "/users";
    assertThat(api.get(users).json()).hasSize(2);
    assertThat(api.post(users, "{\"name\":\"Grace\"}").json().get("id").asInt()).isEqualTo(3);
    assertThat(examples.size()).isPositive();
    int client = 0;
    for (JsonNode example : examples) {
      ApiClient.Result created = api.send("POST", "/__public/mocks", "{\"from\":\"example\",\"id\":\"" + example.get("id").asText() + "\"}",
          "application/json", Map.of("X-Forwarded-For", "198.51.100." + ++client));
      assertThat(created.status()).as(example.get("id").asText()).isEqualTo(201);
      assertThat(created.json().get("skipped")).as(example.get("id").asText()).isEmpty();
    }
  }

  @Test
  void refusesStartersThatCantRunAndCapsStoredRecords() {
    // Given
    server = MockHubServer.start(data, ON);
    ApiClient api = server.api();
    StringBuilder records = new StringBuilder("[");
    for (int i = 0; i < 50; i++) records.append(i == 0 ? "" : ",").append("{\"n\":").append(i).append('}');
    JsonNode created = api.post("/__public/mocks", "{\"from\":\"records\",\"resource\":\"items\",\"records\":" + records + "]}").json();
    String items = created.get("baseUrl").asText() + "/items";

    // When
    int stored = 50;
    ApiClient.Result last = null;
    while (stored <= 200) {
      last = api.post(items, "{\"n\":" + stored + "}");
      if (last.status() != 201) break;
      stored++;
    }
    ApiClient.Result bigRecord = api.post(items, "{\"text\":\"" + "x".repeat(5000) + "\"}");
    ApiClient.Result badName = api.post("/__public/mocks", "{\"from\":\"records\",\"resource\":\"Bad Name!\",\"records\":[{}]}");
    ApiClient.Result badSpec = api.post("/__public/mocks", "{\"from\":\"openapi\",\"text\":\"hello\"}");

    // Then
    assertThat(stored).isEqualTo(200);
    assertThat(last.status()).isEqualTo(400);
    assertThat(last.body()).contains("up to 200 records");
    assertThat(bigRecord.body()).contains("4 KB");
    assertThat(badName.status()).isEqualTo(400);
    assertThat(badSpec.status()).isEqualTo(400);
  }

  private static List<String> stubPaths(ApiClient api, JsonNode created) {
    JsonNode mock = api.send("GET", "/__public/mocks/" + created.get("slug").asText(), null, null,
        Map.of("X-Mock-Token", created.get("token").asText())).json();
    return mock.get("stubs").findValuesAsText("urlPath");
  }
}
