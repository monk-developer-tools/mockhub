package io.mockhub.publictools;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import io.mockhub.api.Jsons;
import java.util.List;
import org.junit.jupiter.api.Test;

class BinStubsTest {

  private static Bins.Settings settings(String reply, String rules) throws Exception {
    return new Bins.Settings(reply(reply), true, BinRule.listFrom(Jsons.MAPPER.readTree(rules)), BinRouting.DEFAULT, BinOptions.DEFAULT);
  }

  private static Bins.Reply reply(String json) throws Exception {
    JsonNode n = Jsons.MAPPER.readTree(json);
    return new Bins.Reply(n.path("status").asInt(200), n.path("contentType").asText(""),
        n.path("body").asText(""), n.path("template").asBoolean(false));
  }

  private static List<JsonNode> nodes(BinStubs.Plan plan) {
    return plan.stubs().stream().map(s -> (JsonNode) s).toList();
  }

  @Test
  void everyRuleKindBecomesAProjectStub() throws Exception {
    // Given
    Bins.Settings settings = settings("{\"status\": 200, \"contentType\": \"application/json\", \"body\": \"{\\\"ok\\\":true}\"}", """
        [{"name": "Users", "method": "GET", "conditions": [{"target": "path", "op": "template", "value": "/users/:id"},
            {"target": "header", "key": "X-Debug", "op": "absent"}, {"target": "query", "key": "page", "op": "equals", "value": "2"}],
          "answers": [{"status": 200, "body": "{{request.path}}", "template": true, "delayMs": 250}]},
         {"name": "Flaky", "method": "ANY", "conditions": [], "answers": [{"weight": 9, "status": 200}, {"weight": 1, "status": 503}]},
         {"name": "Forward", "method": "POST", "conditions": [{"target": "path", "op": "equals", "value": "/forward"}],
          "callout": {"mode": "sync", "method": "GET", "url": "https://api.example.com/lookup", "payload": "custom", "body": "{{request.body}}"}},
         {"name": "Notify", "method": "POST", "conditions": [{"target": "body", "op": "contains", "value": "ship"}],
          "answers": [{"status": 202, "body": "queued"}],
          "callout": {"mode": "async", "method": "same", "url": "https://hooks.example.com/done", "payload": "original",
            "auth": {"type": "bearer", "value": "YOUR_TOKEN"}, "minDelayMs": 1000, "maxDelayMs": 2000}},
         {"name": "Records", "method": "ANY", "conditions": [], "crud": {"path": "/users", "idField": "id", "idType": "number"}}]
        """);

    // When
    BinStubs.Plan plan = BinStubs.of(settings);

    // Then
    assertThat(plan.defaultResponse()).isEqualTo(Jsons.MAPPER.readTree(
        "{\"status\": 200, \"headers\": {\"Content-Type\": \"application/json\"}, \"body\": \"{\\\"ok\\\":true}\", \"templated\": false}"));
    List<JsonNode> stubs = nodes(plan);
    assertThat(stubs).hasSize(5);
    JsonNode users = stubs.get(0);
    assertThat(users.path("name").asText()).isEqualTo("Users");
    assertThat(users.path("priority").asInt()).isEqualTo(10);
    assertThat(users.path("request").path("urlPathPattern").asText()).isEqualTo("/\\Qusers\\E/[^/]+");
    assertThat(users.path("request").path("headers").path("X-Debug").path("absent").asBoolean()).isTrue();
    assertThat(users.path("request").path("queryParameters").path("page").path("equalTo").asText()).isEqualTo("2");
    assertThat(users.path("response").path("body").asText()).isEqualTo("{{request.path}}");
    assertThat(users.path("response").path("transformers").get(0).asText()).isEqualTo("response-template");
    assertThat(users.path("response").path("fixedDelayMilliseconds").asInt()).isEqualTo(250);
    assertThat(users.path("metadata").path("mockhub").path("folder").asText()).isEqualTo("From webhook bin");

    JsonNode flaky = stubs.get(1);
    assertThat(flaky.path("response").path("transformers").get(0).asText()).isEqualTo("mockhub-weighted");
    assertThat(flaky.path("response").path("transformerParameters").path("responses")).hasSize(2);
    assertThat(flaky.path("response").path("transformerParameters").path("responses").get(1).path("status").asInt()).isEqualTo(503);

    JsonNode forward = stubs.get(2);
    assertThat(forward.path("response").path("transformers").get(0).asText()).isEqualTo("mockhub-sync-callout");
    JsonNode call = forward.path("response").path("transformerParameters").path("callout");
    assertThat(call.path("method").asText()).isEqualTo("GET");
    assertThat(call.path("url").asText()).isEqualTo("https://api.example.com/lookup");
    assertThat(call.path("body").asText()).isEqualTo("{{request.body}}");

    JsonNode notify = stubs.get(3);
    JsonNode listener = notify.path("response").path("serveEventListeners").get(0);
    assertThat(listener.path("name").asText()).isEqualTo("mockhub-callout");
    JsonNode parameters = listener.path("parameters");
    assertThat(parameters.path("method").asText()).isEqualTo("{{request.method}}");
    assertThat(parameters.path("forward").asBoolean()).isTrue();
    assertThat(parameters.path("headers").path("Authorization").asText()).isEqualTo("Bearer YOUR_TOKEN");
    assertThat(parameters.path("delay").path("milliseconds").asInt()).isEqualTo(1000);
    assertThat(plan.notes()).contains("Notify: the background call waits a fixed 1000 ms instead of 1000–2000 ms");

    JsonNode records = stubs.get(4);
    assertThat(records.path("request").path("urlPathPattern").asText()).isEqualTo("/users(/[^/]+)?/?");
    assertThat(records.path("response").path("transformers").get(0).asText()).isEqualTo("mockhub-crud");
    assertThat(records.path("response").path("transformerParameters").path("idField").asText()).isEqualTo("id");
  }

  @Test
  void bodyFieldChecksBecomePatternsAndStateHelpersAreFlagged() throws Exception {
    // Given
    Bins.Settings settings = settings("{}", """
        [{"name": "Deep", "method": "POST", "conditions": [
            {"target": "json", "key": "$.type", "op": "equals", "value": "payment.failed"},
            {"target": "xml", "key": "//user", "op": "exists"},
            {"target": "soap_action", "op": "equals", "value": "GetUser"},
            {"target": "graphql", "op": "equals", "value": "Launches"}],
          "answers": [{"status": 402, "body": "order {{counter 'orders'}}", "template": true}]}]
        """);

    // When
    BinStubs.Plan plan = BinStubs.of(settings);

    // Then
    JsonNode request = plan.stubs().get(0).path("request");
    JsonNode patterns = request.path("bodyPatterns");
    assertThat(patterns.get(0).path("matchesJsonPath").path("expression").asText()).isEqualTo("$.type");
    assertThat(patterns.get(0).path("matchesJsonPath").path("equalTo").asText()).isEqualTo("payment.failed");
    assertThat(patterns.get(1).path("matchesXPath").asText()).isEqualTo("//user");
    assertThat(request.path("headers").path("SOAPAction").path("equalTo").asText()).isEqualTo("GetUser");
    assertThat(patterns.get(2).path("matchesJsonPath").asText()).isEqualTo("$[?(@.operationName == 'Launches')]");
    assertThat(plan.notes()).anyMatch(n -> n.startsWith("Bin memory helpers"));
  }

  @Test
  void rulesThatCannotConvertAreLeftOutWithAReason() throws Exception {
    // Given
    Bins.Settings settings = settings("{}", """
        [{"name": "Form", "method": "POST", "conditions": [{"target": "form", "key": "a", "op": "equals", "value": "1"}],
          "answers": [{"status": 200}]},
         {"name": "NoPath", "method": "GET", "conditions": [{"target": "path", "op": "absent"}], "answers": [{"status": 200}]},
         {"name": "Kept", "method": "GET", "conditions": [], "answers": [{"status": 204}]}]
        """);

    // When
    BinStubs.Plan plan = BinStubs.of(settings);

    // Then
    assertThat(plan.stubs()).hasSize(1);
    assertThat(plan.stubs().get(0).path("name").asText()).isEqualTo("Kept");
    assertThat(plan.notes()).containsExactly(
        "Form: form-field checks only exist in bins; check the body text instead",
        "NoPath: a path can only be checked with equals, starts_with, contains, matches or a pattern");
  }

  @Test
  void disabledRulesKeepTheirSwitchAndABinWithoutRulesSaysSo() throws Exception {
    // Given
    Bins.Settings settings = settings("{}", """
        [{"name": "Off", "enabled": false, "method": "GET", "conditions": [], "answers": [{"status": 200}]}]
        """);
    Bins.Settings empty = settings("{}", "[]");

    // When
    BinStubs.Plan plan = BinStubs.of(settings);
    BinStubs.Plan bare = BinStubs.of(empty);

    // Then
    assertThat(plan.stubs().get(0).path("metadata").path("mockhub").path("disabled").asBoolean()).isTrue();
    assertThat(plan.stubs().get(0).path("request").path("customMatcher").path("name").asText()).isEqualTo("mockhub-disabled");
    assertThat(bare.stubs()).isEmpty();
    assertThat(bare.notes()).containsExactly("The bin had no rules, so the project only has the default answer");
  }
}
