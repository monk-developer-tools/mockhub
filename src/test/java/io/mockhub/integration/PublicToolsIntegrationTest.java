package io.mockhub.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.sun.net.httpserver.HttpServer;
import io.mockhub.api.Jsons;
import io.mockhub.support.Fixtures;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PublicToolsIntegrationTest {

  private static final Map<String, String> ON = Map.of("MOCKHUB_PUBLIC_TOOLS", "true");
  private static final String SCHEMA = """
      type Query { launches(limit: Int): [Launch!]! launch(id: ID!): Launch }
      type Launch { id: ID! mission: String! rocket: Rocket! }
      type Rocket { name: String! }
      """;

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
  void aBinsSettingsRequireAHeaderLimitTheRateAddLatencyAndControlCors() {
    // Given
    server = MockHubServer.start(data, ON);
    JsonNode bin = server.api().post("/__public/bins", "").json();
    String id = bin.get("id").asText();
    Map<String, String> owner = Map.of("X-Bin-Token", bin.get("token").asText());
    String inspect = "/__public/inspect/" + id;
    String base = "/__public/bins/" + id;
    ApiClient.Result saved = server.api().send("PUT", inspect + "/options",
        "{\"cors\": true, \"latencyMs\": 300, \"rateLimit\": {\"requests\": 2, \"per\": \"minute\"}, \"requiredHeader\": {\"name\": \"X-Api-Key\", \"value\": \"k1\"}}",
        "application/json", owner);
    int invalid = server.api().send("PUT", inspect + "/options", "{\"latencyMs\": 99999}", "application/json", owner).status();
    server.close();
    server = MockHubServer.start(data, ON);
    ApiClient api = server.api();
    Map<String, String> key = Map.of("X-Api-Key", "k1");

    // When
    ApiClient.Result preflight = api.send("OPTIONS", base + "/orders", null, null, Map.of("Access-Control-Request-Method", "QUERY", "Access-Control-Request-Headers", "x-api-key, x-trace"));
    ApiClient.Result noKey = api.send("POST", base + "/orders", "{}", "application/json");
    long started = System.nanoTime();
    ApiClient.Result first = api.send("POST", base + "/orders", "{}", "application/json", key);
    long tookMs = (System.nanoTime() - started) / 1_000_000;
    ApiClient.Result second = api.send("POST", base + "/orders", "{}", "application/json", key);
    ApiClient.Result third = api.send("POST", base + "/orders", "{}", "application/json", key);
    api.send("PUT", inspect + "/options", "{\"cors\": false}", "application/json", owner);
    ApiClient.Result plainPreflight = api.send("OPTIONS", base + "/orders", null, null, Map.of("Access-Control-Request-Method", "PUT"));
    JsonNode log = api.send("GET", inspect, null, null, owner).json();
    JsonNode settings = api.send("GET", inspect + "/settings", null, null, owner).json();

    // Then
    assertThat(saved.status()).isEqualTo(200);
    assertThat(saved.json().at("/options/rateLimit/per").asText()).isEqualTo("minute");
    assertThat(invalid).isEqualTo(400);
    assertThat(preflight.status()).isEqualTo(204);
    assertThat(preflight.header("Access-Control-Allow-Methods")).isEqualTo("QUERY");
    assertThat(preflight.header("Access-Control-Allow-Headers")).isEqualTo("x-api-key, x-trace");
    assertThat(noKey.status()).isEqualTo(401);
    assertThat(noKey.body()).contains("X-Api-Key");
    assertThat(first.status()).isEqualTo(200);
    assertThat(first.header("Access-Control-Allow-Origin")).isEqualTo("*");
    assertThat(first.header("Access-Control-Expose-Headers")).isEqualTo("*");
    assertThat(tookMs).isGreaterThanOrEqualTo(300);
    assertThat(second.status()).isEqualTo(200);
    assertThat(third.status()).isEqualTo(429);
    assertThat(third.header("Retry-After")).isNotBlank();
    assertThat(plainPreflight.header("Access-Control-Allow-Origin")).isNull();
    assertThat(plainPreflight.status()).isEqualTo(200);
    assertThat(log.at("/requests/0/method").asText()).isEqualTo("OPTIONS");
    assertThat(log.at("/requests/0/outcome/by").asText()).isEqualTo("default");
    assertThat(log.at("/requests/1/outcome/by").asText()).isEqualTo("limit");
    assertThat(log.at("/requests/4/outcome/by").asText()).isEqualTo("auth");
    assertThat(settings.at("/options/cors").asBoolean()).isFalse();
    assertThat(settings.at("/options/rateLimit").isNull()).isTrue();
  }

  @Test
  void aBinRuleCallsOutAndWaitsOrAnswersAtOnceAndCallsOutLater() throws Exception {
    // Given
    server = MockHubServer.start(data, Map.of("MOCKHUB_PUBLIC_TOOLS", "true", "MOCKHUB_BIN_ALLOW_PRIVATE", "true"));
    ApiClient api = server.api();
    JsonNode caller = api.post("/__public/bins", "").json();
    JsonNode target = api.post("/__public/bins", "").json();
    String id = caller.get("id").asText();
    Map<String, String> owner = Map.of("X-Bin-Token", caller.get("token").asText());
    Map<String, String> targetOwner = Map.of("X-Bin-Token", target.get("token").asText());
    String targetUrl = server.baseUrl() + "/__public/bins/" + target.get("id").asText();
    api.send("PUT", "/__public/inspect/" + target.get("id").asText() + "/response", "{\"status\": 201, \"contentType\": \"application/json\", \"body\": \"{\\\"stored\\\":true}\"}",
        "application/json", targetOwner);
    String rules = """
        {"rules": [
          {"name": "Relay", "conditions": [{"target": "path", "op": "equals", "value": "/sync"}],
           "callout": {"mode": "sync", "method": "PUT", "url": "%1$s/orders/{{jsonPath request.body '$.id'}}", "payload": "custom",
                       "body": "{\\"relayed\\": \\"{{jsonPath request.body '$.id'}}\\"}", "auth": {"type": "bearer", "value": "tok_1"}}},
          {"name": "Ack", "conditions": [{"target": "path", "op": "equals", "value": "/async"}],
           "answers": [{"status": 202, "body": "{\\"queued\\":true}"}],
           "callout": {"mode": "async", "url": "%1$s/notify", "payload": "original", "auth": {"type": "header", "name": "X-Key", "value": "k9"},
                       "minDelayMs": 100, "maxDelayMs": 200}}
        ]}
        """.formatted(targetUrl);
    ApiClient.Result saved = api.send("PUT", "/__public/inspect/" + id + "/rules", rules, "application/json", owner);

    // When
    ApiClient.Result waited = api.post("/__public/bins/" + id + "/sync", "{\"id\":\"o7\"}");
    ApiClient.Result acked = api.post("/__public/bins/" + id + "/async", "{\"n\":1}");
    JsonNode right = api.send("GET", "/__public/inspect/" + id, null, null, owner).json();
    JsonNode reached = Jsons.MAPPER.createObjectNode();
    for (int i = 0; i < 50 && reached.path("total").asLong() < 2; i++) {
      Thread.sleep(100);
      reached = api.send("GET", "/__public/inspect/" + target.get("id").asText(), null, null, targetOwner).json();
    }
    JsonNode later = right;
    for (int i = 0; i < 50 && later.at("/requests/0/callout/state").asText().equals("scheduled"); i++) {
      Thread.sleep(100);
      later = api.send("GET", "/__public/inspect/" + id, null, null, owner).json();
    }

    // Then
    assertThat(saved.status()).isEqualTo(200);
    assertThat(saved.json().at("/rules/0/callout/auth/type").asText()).isEqualTo("bearer");
    assertThat(waited.status()).isEqualTo(201);
    assertThat(waited.body()).isEqualTo("{\"stored\":true}");
    assertThat(waited.header("X-MockHub-Bin")).isEqualTo(id);
    assertThat(acked.status()).isEqualTo(202);
    assertThat(right.at("/requests/0/callout/state").asText()).isEqualTo("scheduled");
    assertThat(right.at("/requests/1/outcome/by").asText()).isEqualTo("callout");
    assertThat(right.at("/requests/1/callout/state").asText()).isEqualTo("sent");
    assertThat(right.at("/requests/1/callout/status").asInt()).isEqualTo(201);
    assertThat(right.at("/requests/1/callout/url").asText()).isEqualTo(targetUrl + "/orders/o7");
    assertThat(reached.get("total").asLong()).isEqualTo(2);
    assertThat(reached.at("/requests/0/path").asText()).isEqualTo("/notify");
    assertThat(reached.at("/requests/0/body").asText()).isEqualTo("{\"n\":1}");
    assertThat(headerOf(reached.at("/requests/0/headers"), "X-Key")).isEqualTo("k9");
    assertThat(reached.at("/requests/1/method").asText()).isEqualTo("PUT");
    assertThat(reached.at("/requests/1/path").asText()).isEqualTo("/orders/o7");
    assertThat(reached.at("/requests/1/body").asText()).isEqualTo("{\"relayed\": \"o7\"}");
    assertThat(headerOf(reached.at("/requests/1/headers"), "Authorization")).isEqualTo("Bearer tok_1");
    assertThat(later.at("/requests/0/callout/state").asText()).isEqualTo("sent");
    assertThat(later.at("/requests/0/callout/status").asInt()).isEqualTo(201);
    assertThat(later.at("/requests/0/callout/waitMs").asLong()).isBetween(100L, 200L);
  }

  @Test
  void aBinCrudRouteStoresRecordsThatTheOwnerCanSeedReadAndClear() {
    // Given
    server = MockHubServer.start(data, ON);
    ApiClient api = server.api();
    JsonNode bin = api.post("/__public/bins", "").json();
    String id = bin.get("id").asText();
    Map<String, String> owner = Map.of("X-Bin-Token", bin.get("token").asText());
    String inspect = "/__public/inspect/" + id;
    String base = "/__public/bins/" + id;
    ApiClient.Result saved = api.send("PUT", inspect + "/rules",
        "{\"rules\": [{\"name\": \"Users\", \"crud\": {\"path\": \"/users\", \"idType\": \"number\"}}]}", "application/json", owner);
    ApiClient.Result seeded = api.send("PUT", inspect + "/data",
        "{\"route\": {\"path\": \"/users\"}, \"records\": [{\"name\": \"Ada\"}, {\"name\": \"Bob\"}]}", "application/json", owner);
    String viewKey = api.send("GET", inspect + "/settings", null, null, owner).json().get("viewKey").asText();
    server.close();
    server = MockHubServer.start(data, ON);
    api = server.api();

    // When
    ApiClient.Result created = api.post(base + "/users", "{\"name\":\"Cy\"}");
    ApiClient.Result listed = api.send("GET", base + "/users?_sort=name&_order=desc", null, null);
    JsonNode viewed = api.send("GET", inspect + "/data", null, null, Map.of("X-Bin-Token", viewKey)).json();
    int viewerClears = api.send("DELETE", inspect + "/data", null, null, Map.of("X-Bin-Token", viewKey)).status();
    JsonNode cleared = api.send("DELETE", inspect + "/data?collection=users", null, null, owner).json();
    ApiClient.Result empty = api.send("GET", base + "/users", null, null);
    JsonNode log = api.send("GET", inspect, null, null, owner).json();

    // Then
    assertThat(saved.status()).isEqualTo(200);
    assertThat(saved.json().at("/rules/0/crud/path").asText()).isEqualTo("/users");
    assertThat(seeded.json().get("seeded").asInt()).isEqualTo(2);
    assertThat(created.status()).isEqualTo(201);
    assertThat(created.json().get("id").asInt()).isEqualTo(3);
    assertThat(created.header("Location")).isEqualTo(base + "/users/3");
    assertThat(listed.header("X-Total-Count")).isEqualTo("3");
    assertThat(listed.json().get(0).get("name").asText()).isEqualTo("Cy");
    assertThat(viewed.at("/collections/0/name").asText()).isEqualTo("users");
    assertThat(viewed.at("/collections/0/count").asInt()).isEqualTo(3);
    assertThat(viewerClears).isEqualTo(403);
    assertThat(cleared.get("collections").size()).isZero();
    assertThat(empty.body()).isEqualTo("[ ]");
    assertThat(log.at("/requests/0/outcome/by").asText()).isEqualTo("crud");
    assertThat(log.at("/requests/0/outcome/rule").asText()).isEqualTo("Users");
  }

  @Test
  void aBinsRulesStateAndMultipartPartsWorkOneByOneFromAScript() {
    // Given
    server = MockHubServer.start(data, ON);
    ApiClient api = server.api();
    JsonNode bin = api.post("/__public/bins", "").json();
    String id = bin.get("id").asText();
    Map<String, String> owner = Map.of("X-Bin-Token", bin.get("token").asText());
    String inspect = "/__public/inspect/" + id;
    String viewKey = api.send("GET", inspect + "/settings", null, null, owner).json().get("viewKey").asText();
    String multipart = "--b1\r\nContent-Disposition: form-data; name=\"doc\"; filename=\"a<b>.html\"\r\nContent-Type: text/html\r\n\r\n<p>hi</p>\r\n--b1--\r\n";

    // When
    ApiClient.Result first = api.send("POST", inspect + "/rules", "{\"id\": \"paid\", \"name\": \"Paid\", \"answers\": [{\"status\": 200}]}", "application/json", owner);
    ApiClient.Result second = api.send("POST", inspect + "/rules", "{\"name\": \"Failed\", \"answers\": [{\"status\": 402}]}", "application/json", owner);
    String secondId = second.json().get("id").asText();
    int taken = api.send("POST", inspect + "/rules", "{\"id\": \"paid\", \"answers\": [{}]}", "application/json", owner).status();
    ApiClient.Result patched = api.send("PATCH", inspect + "/rules/paid", "{\"description\": \"200 for paid\"}", "application/json", owner);
    ApiClient.Result reordered = api.send("PUT", inspect + "/rules/order", "{\"ids\": [\"" + secondId + "\", \"paid\"]}", "application/json", owner);
    int missing = api.send("GET", inspect + "/rules/nope", null, null, owner).status();
    int viewerEdits = api.send("DELETE", inspect + "/rules/paid", null, null, Map.of("X-Bin-Token", viewKey)).status();
    ApiClient.Result deleted = api.send("DELETE", inspect + "/rules/" + secondId, null, null, owner);
    ApiClient.Result counter = api.send("PUT", inspect + "/state/counters/orders%20today", "{\"value\": 7}", "application/json", owner);
    ApiClient.Result list = api.send("PUT", inspect + "/state/lists/seen", "{\"value\": [\"a\", \"b\"]}", "application/json", owner);
    int badKind = api.send("PUT", inspect + "/state/maps/x", "{\"value\": 1}", "application/json", owner).status();
    ApiClient.Result unset = api.send("DELETE", inspect + "/state/lists/seen", null, null, owner);
    api.send("POST", "/__public/bins/" + id + "/upload", multipart, "multipart/form-data; boundary=b1");
    long seq = api.send("GET", inspect, null, null, owner).json().at("/requests/0/seq").asLong();
    JsonNode partList = api.send("GET", inspect + "/requests/" + seq + "/parts", null, null, Map.of("X-Bin-Token", viewKey)).json();
    ApiClient.Result part = api.send("GET", inspect + "/requests/" + seq + "/parts/0", null, null, owner);
    int noPart = api.send("GET", inspect + "/requests/" + seq + "/parts/5", null, null, owner).status();

    // Then
    assertThat(first.status()).isEqualTo(201);
    assertThat(first.json().get("id").asText()).isEqualTo("paid");
    assertThat(second.status()).isEqualTo(201);
    assertThat(taken).isEqualTo(409);
    assertThat(patched.json().get("description").asText()).isEqualTo("200 for paid");
    assertThat(patched.json().at("/answers/0/status").asInt()).isEqualTo(200);
    assertThat(reordered.json().at("/rules/0/id").asText()).isEqualTo(secondId);
    assertThat(missing).isEqualTo(404);
    assertThat(viewerEdits).isEqualTo(403);
    assertThat(deleted.json().get("rules").size()).isEqualTo(1);
    assertThat(api.send("GET", inspect + "/rules", null, null, owner).json().at("/rules/0/id").asText()).isEqualTo("paid");
    assertThat(counter.json().at("/counters/orders today").asLong()).isEqualTo(7);
    assertThat(list.json().at("/lists/seen/1").asText()).isEqualTo("b");
    assertThat(badKind).isEqualTo(400);
    assertThat(unset.json().get("lists").has("seen")).isFalse();
    assertThat(partList.at("/parts/0/filename").asText()).isEqualTo("a<b>.html");
    assertThat(partList.at("/parts/0/size").asInt()).isEqualTo(9);
    assertThat(part.status()).isEqualTo(200);
    assertThat(part.body()).isEqualTo("<p>hi</p>");
    assertThat(part.header("Content-Disposition")).isEqualTo("attachment; filename=\"a_b_.html\"");
    assertThat(part.header("Content-Security-Policy")).isEqualTo("sandbox");
    assertThat(noPart).isEqualTo(404);
  }

  private static String headerOf(JsonNode headers, String name) {
    for (var it = headers.fields(); it.hasNext(); ) {
      var e = it.next();
      if (e.getKey().equalsIgnoreCase(name)) return e.getValue().asText();
    }
    return null;
  }

  @Test
  void aBinsLogFiltersSharesDeletesAndExportsRequests() {
    // Given
    server = MockHubServer.start(data, ON);
    ApiClient api = server.api();
    JsonNode bin = api.post("/__public/bins", "").json();
    String id = bin.get("id").asText();
    Map<String, String> owner = Map.of("X-Bin-Token", bin.get("token").asText());
    String inspect = "/__public/inspect/" + id;
    api.send("POST", "/__public/bins/" + id + "/pay", "{\"type\":\"payment.failed\"}", "application/json");
    api.send("PUT", "/__public/bins/" + id + "/orders/7", "{\"id\":7}", "application/json");
    api.send("POST", "/__public/bins/" + id + "/pay", "{\"type\":\"payment.paid\"}", "application/json");
    String view = api.send("GET", inspect + "/settings", null, null, owner).json().get("viewKey").asText();

    // When
    JsonNode filtered = api.send("GET", inspect + "?method=POST&q=failed", null, null, owner).json();
    String key = api.send("GET", inspect + "/requests/2/share", null, null, owner).json().get("key").asText();
    JsonNode shared = api.send("GET", inspect + "/requests/2", null, null, Map.of("X-Bin-Token", key)).json();
    int sharedOther = api.send("GET", inspect + "/requests/1", null, null, Map.of("X-Bin-Token", key)).status();
    int viewerDelete = api.send("DELETE", inspect + "/requests/1", null, null, Map.of("X-Bin-Token", view)).status();
    int viewerShare = api.send("GET", inspect + "/requests/1/share", null, null, Map.of("X-Bin-Token", view)).status();
    int deleted = api.send("DELETE", inspect + "/requests/1", null, null, owner).status();
    JsonNode after = api.send("GET", inspect, null, null, owner).json();
    JsonNode har = api.send("GET", inspect + "/export", null, null, Map.of("X-Bin-Token", view)).json();
    JsonNode json = api.send("GET", inspect + "/export?format=json&method=PUT", null, null, owner).json();
    int badFilter = api.send("GET", inspect + "?status=teapot", null, null, owner).status();

    // Then
    assertThat(filtered.get("requests")).hasSize(1);
    assertThat(filtered.at("/requests/0/seq").asLong()).isEqualTo(1);
    assertThat(shared.get("path").asText()).isEqualTo("/orders/7");
    assertThat(shared.get("bin").asText()).isEqualTo(id);
    assertThat(sharedOther).isEqualTo(404);
    assertThat(viewerDelete).isEqualTo(403);
    assertThat(viewerShare).isEqualTo(403);
    assertThat(deleted).isEqualTo(200);
    assertThat(after.get("requests")).hasSize(2);
    assertThat(after.get("deleted").asInt()).isEqualTo(1);
    assertThat(har.at("/log/version").asText()).isEqualTo("1.2");
    assertThat(har.at("/log/entries")).hasSize(2);
    assertThat(har.at("/log/entries/1/request/url").asText()).endsWith("/__public/bins/" + id + "/orders/7");
    assertThat(har.at("/log/entries/1/request/postData/text").asText()).isEqualTo("{\"id\":7}");
    assertThat(har.at("/log/entries/1/response/status").asInt()).isEqualTo(200);
    assertThat(json.get("exported").asInt()).isEqualTo(1);
    assertThat(json.at("/requests/0/method").asText()).isEqualTo("PUT");
    assertThat(badFilter).isEqualTo(400);
  }

  @Test
  void aBinKeepsCountersAndSavedValuesAcrossRequestsAndRestarts() {
    // Given
    server = MockHubServer.start(data, ON);
    JsonNode bin = server.api().post("/__public/bins", "").json();
    String id = bin.get("id").asText();
    Map<String, String> owner = Map.of("X-Bin-Token", bin.get("token").asText());
    String rules = """
        {"rulesEnabled": true, "rules": [
          {"name": "Check", "method": "POST", "conditions": [{"target": "path", "op": "equals", "value": "/check"}],
           "answers": [{"status": 200, "template": true, "body": "{{isEmail (jsonPath request.body '$.email') 'Enter a valid e-mail'}}ok"}]},
          {"name": "Count", "method": "POST", "answers": [{"status": 200, "template": true,
           "body": "{{counter 'orders'}} {{addToList 'ids' (jsonPath request.body '$.id')}}{{savedValue 'last' default='none'}}{{saveValue 'last' (jsonPath request.body '$.id')}}"}]}]}
        """;
    server.api().send("PUT", "/__public/inspect/" + id + "/rules", rules, "application/json", owner);
    String base = "/__public/bins/" + id;
    String first = server.api().send("POST", base + "/orders", "{\"id\":\"a\"}", "application/json").body();
    server.close();
    server = MockHubServer.start(data, ON);
    ApiClient api = server.api();

    // When
    String second = api.send("POST", base + "/orders", "{\"id\":\"b\"}", "application/json").body();
    ApiClient.Result refused = api.send("POST", base + "/check", "{\"email\":\"nope\"}", "application/json");
    JsonNode state = api.send("GET", "/__public/inspect/" + id + "/state", null, null, owner).json();
    int cleared = api.send("DELETE", "/__public/inspect/" + id + "/state", null, null, owner).status();
    String afterClear = api.send("POST", base + "/orders", "{\"id\":\"c\"}", "application/json").body();
    int stranger = api.send("GET", "/__public/inspect/" + id + "/state", null, null, Map.of("X-Bin-Token", "nope")).status();

    // Then
    assertThat(first).isEqualTo("1 none");
    assertThat(second).isEqualTo("2 a");
    assertThat(refused.status()).isEqualTo(400);
    assertThat(refused.body()).contains("Enter a valid e-mail");
    assertThat(state.at("/counters/orders").asInt()).isEqualTo(2);
    assertThat(state.at("/values/last").asText()).isEqualTo("b");
    assertThat(state.at("/lists/ids").toString()).isEqualTo("[\"a\",\"b\"]");
    assertThat(cleared).isEqualTo(200);
    assertThat(afterClear).isEqualTo("1 none");
    assertThat(stranger).isEqualTo(404);
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
  void startsAGraphqlMockFromASchemaThatAnswersQueriesAndListsExamples() {
    // Given
    server = MockHubServer.start(data, ON);
    ApiClient api = server.api();

    // When
    ApiClient.Result created = api.post("/__public/mocks", Jsons.object().put("from", "graphql").put("text", SCHEMA).toString());
    String slug = created.json().get("slug").asText();
    String token = created.json().get("token").asText();
    ApiClient.Result answer = api.post("/" + slug + "/graphql", "{\"query\":\"{ launches(limit: 2) { id mission rocket { name } } }\"}");
    JsonNode explorer = api.send("GET", "/__public/mocks/" + slug + "/graphql", null, null, Map.of("X-Mock-Token", token)).json();
    JsonNode endpoint = explorer.get("endpoints").get(0);
    JsonNode example = endpoint.get("operations").get(0);
    ApiClient.Result exampleAnswer = api.post("/" + slug + endpoint.get("path").asText(),
        Jsons.object().put("query", example.get("query").asText()).set("variables", example.get("variables")).toString());
    ApiClient.Result badSchema = api.post("/__public/mocks", "{\"from\":\"graphql\",\"text\":\"type Query {\"}");
    ApiClient.Result noToken = api.get("/__public/mocks/" + slug + "/graphql");

    // Then
    assertThat(created.status()).isEqualTo(201);
    assertThat(answer.status()).isEqualTo(200);
    assertThat(answer.json().at("/data/launches")).hasSize(2);
    assertThat(answer.json().at("/data/launches/0/rocket/name").isTextual()).isTrue();
    assertThat(endpoint.get("path").asText()).isEqualTo("/graphql");
    assertThat(endpoint.get("operations").findValuesAsText("field")).contains("launches", "launch");
    assertThat(example.get("query").asText()).contains("launches");
    assertThat(exampleAnswer.status()).isEqualTo(200);
    assertThat(exampleAnswer.json().has("errors")).isFalse();
    assertThat(badSchema.status()).isEqualTo(400);
    assertThat(noToken.status()).isEqualTo(404);
  }

  @Test
  void startsInstantMocksFromASpecOrSchemaAtAUrlAndRefusesPrivateAddressesByDefault() throws IOException {
    // Given
    HttpServer specs = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    serve(specs, "/petstore.yaml", Fixtures.resource("petstore.yaml"));
    serve(specs, "/schema.graphql", SCHEMA);
    specs.start();
    String base = "http://127.0.0.1:" + specs.getAddress().getPort();
    try {
      server = MockHubServer.start(data, Map.of("MOCKHUB_PUBLIC_TOOLS", "true", "MOCKHUB_PLAYGROUND_ALLOW_PRIVATE", "true"));
      ApiClient open = server.api();

      // When
      JsonNode spec = open.post("/__public/mocks", Jsons.object().put("from", "openapi").put("url", base + "/petstore.yaml").toString()).json();
      ApiClient.Result graph = open.send("POST", "/__public/mocks", Jsons.object().put("from", "graphql").put("url", base + "/schema.graphql").toString(),
          "application/json", Map.of("X-Forwarded-For", "198.51.100.7"));
      ApiClient.Result missing = open.send("POST", "/__public/mocks", Jsons.object().put("from", "openapi").put("url", base + "/nothing.yaml").toString(),
          "application/json", Map.of("X-Forwarded-For", "198.51.100.8"));
      ApiClient.Result both = open.send("POST", "/__public/mocks", Jsons.object().put("from", "openapi").put("url", base + "/petstore.yaml").put("text", "x").toString(),
          "application/json", Map.of("X-Forwarded-For", "198.51.100.9"));
      server.close();
      server = MockHubServer.start(data, ON);
      ApiClient.Result guarded = server.api().post("/__public/mocks", Jsons.object().put("from", "openapi").put("url", base + "/petstore.yaml").toString());

      // Then
      assertThat(server.api().get(spec.get("baseUrl").asText() + "/pets").status()).isEqualTo(200);
      assertThat(graph.status()).isEqualTo(201);
      assertThat(server.api().post(graph.json().get("baseUrl").asText() + "/graphql", "{\"query\":\"{ launch(id: 1) { mission } }\"}").json()
          .at("/data/launch/mission").isTextual()).isTrue();
      assertThat(missing.status()).isEqualTo(502);
      assertThat(missing.body()).contains("answered 404");
      assertThat(both.status()).isEqualTo(400);
      assertThat(both.body()).contains("not both");
      assertThat(guarded.status()).isEqualTo(400);
      assertThat(guarded.body()).contains("MOCKHUB_PLAYGROUND_ALLOW_PRIVATE");
    } finally {
      specs.stop(0);
    }
  }

  private static void serve(HttpServer http, String path, String text) {
    http.createContext(path, exchange -> {
      byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
      exchange.sendResponseHeaders(200, bytes.length);
      try (OutputStream out = exchange.getResponseBody()) {
        out.write(bytes);
      }
    });
  }

  @Test
  void generatesTwoFactorCodesFromASecretOrAnOtpauthLink() {
    // Given
    server = MockHubServer.start(data, ON);
    ApiClient api = server.api();
    String secret = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";

    // When
    ApiClient.Result text = api.get("/__public/totp?secret=" + secret + "&digits=8&time=59&format=text");
    ApiClient.Result now = api.get("/__public/totp?secret=" + secret);
    ApiClient.Result linked = api.post("/__public/totp",
        "{\"uri\": \"otpauth://totp/Shop:ada?secret=" + secret + "&issuer=Shop&digits=8\", \"time\": 1234567890}");
    ApiClient.Result bad = api.get("/__public/totp?secret=not-base32!");

    // Then
    assertThat(text.status()).isEqualTo(200);
    assertThat(text.body()).isEqualTo("94287082");
    assertThat(now.json().get("code").asText()).matches("\\d{6}");
    assertThat(now.json().get("secondsRemaining").asInt()).isBetween(1, 30);
    assertThat(linked.json().get("code").asText()).isEqualTo("89005924");
    assertThat(linked.json().get("issuer").asText()).isEqualTo("Shop");
    assertThat(bad.status()).isEqualTo(400);
    assertThat(bad.body()).contains("Base32");
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

  @Test
  void aBinSavesIntoASignedInUsersProjectWithItsRulesRecordsAndDefaultAnswer() {
    // Given
    server = MockHubServer.start(data, Map.of("MOCKHUB_PUBLIC_TOOLS", "true", "MOCKHUB_USERS", "admin:pw:admin"));
    ApiClient api = server.api();
    JsonNode bin = api.post("/__public/bins", "").json();
    String id = bin.get("id").asText();
    Map<String, String> owner = Map.of("X-Bin-Token", bin.get("token").asText());
    String inspect = "/__public/inspect/" + id;
    api.send("PUT", inspect + "/rules", """
        {"rules": [
          {"name": "Hello", "method": "GET", "conditions": [{"target": "path", "op": "equals", "value": "/hello"}],
           "answers": [{"status": 200, "body": "hi"}]},
          {"name": "Users", "crud": {"path": "/users", "idType": "number"}},
          {"name": "Form thing", "conditions": [{"target": "form", "key": "a", "op": "equals", "value": "1"}],
           "answers": [{"status": 200}]}]}
        """, "application/json", owner);
    api.send("PUT", inspect + "/data", "{\"route\": {\"path\": \"/users\"}, \"records\": [{\"name\": \"Ada\"}]}", "application/json", owner);
    String token = server.api().post("/__admin/mockhub/api/auth/login", "{\"username\":\"admin\",\"password\":\"pw\"}").json().get("token").asText();
    Map<String, String> signedOwner = Map.of("X-Bin-Token", bin.get("token").asText(), "Authorization", "Bearer " + token);
    ApiClient admin = server.api().login("admin", "pw");

    // When
    int anonymous = api.send("POST", inspect + "/project", "{}", "application/json", owner).status();
    int wrongToken = api.send("POST", inspect + "/project", "{}", "application/json",
        Map.of("X-Bin-Token", "nope", "Authorization", "Bearer " + token)).status();
    ApiClient.Result saved = api.send("POST", inspect + "/project", "{\"name\": \"My webhooks\"}", "application/json", signedOwner);
    JsonNode project = saved.json();
    String projectId = project.get("projectId").asText();
    ApiClient.Result stubs = admin.get("/__admin/mockhub/api/projects/" + projectId + "/stubs");
    ApiClient.Result hello = api.send("GET", "/my-webhooks/hello", null, null, Map.of());
    ApiClient.Result record = api.send("GET", "/my-webhooks/users", null, null, Map.of());
    ApiClient.Result fallback = api.send("GET", "/my-webhooks/anything", null, null, Map.of());

    // Then
    assertThat(anonymous).isEqualTo(401);
    assertThat(wrongToken).isEqualTo(404);
    assertThat(saved.status()).isEqualTo(201);
    assertThat(project.get("name").asText()).isEqualTo("My webhooks");
    assertThat(project.get("slug").asText()).isEqualTo("my-webhooks");
    assertThat(project.get("url").asText()).isEqualTo("/my-webhooks");
    assertThat(project.get("ui").asText()).isEqualTo("/__admin/mockhub/ui/projects/" + projectId);
    assertThat(project.get("notes").toString()).contains("form-field checks only exist in bins");
    assertThat(stubs.status()).isEqualTo(200);
    assertThat(stubs.json()).hasSize(2);
    assertThat(stubs.json().get(0).get("name").asText()).isEqualTo("Hello");
    assertThat(hello.status()).isEqualTo(200);
    assertThat(hello.body()).isEqualTo("hi");
    assertThat(record.status()).isEqualTo(200);
    assertThat(record.json().get(0).get("name").asText()).isEqualTo("Ada");
    assertThat(fallback.status()).isEqualTo(200);
    assertThat(fallback.body()).isEqualTo("{\"ok\":true}");
  }

  private static List<String> stubPaths(ApiClient api, JsonNode created) {
    JsonNode mock = api.send("GET", "/__public/mocks/" + created.get("slug").asText(), null, null,
        Map.of("X-Mock-Token", created.get("token").asText())).json();
    return mock.get("stubs").findValuesAsText("urlPath");
  }
}
