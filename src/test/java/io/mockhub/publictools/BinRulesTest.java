package io.mockhub.publictools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import io.mockhub.api.Jsons;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BinRulesTest {

  private static JsonNode json(String text) {
    try {
      return Jsons.MAPPER.readTree(text);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private static BinRule rule(String text) {
    return BinRule.from(json(text));
  }

  private static BinMatcher.Call call(String method, String path, Map<String, String> headers, String body) {
    PublicRequest request = new PublicRequest(method, "/bins/abcdefghijkm" + path, "attempt=2", Map.of("attempt", List.of("2")), headers, body,
        body.length(), "203.0.113.9");
    return new BinMatcher.Call(method, path, request);
  }

  @Test
  void theFirstEnabledRuleThatMatchesAnswers() {
    // Given
    List<BinRule> rules = BinRule.listFrom(json("""
        [{"name": "Off", "enabled": false, "answers": [{"status": 500}]},
         {"name": "Failed payments", "method": "POST", "conditions": [{"target": "json", "key": "$.data.object.status", "op": "equals", "value": "failed"}],
          "answers": [{"status": 402}]},
         {"name": "Any payment", "method": "POST", "conditions": [{"target": "path", "op": "starts_with", "value": "/payments"}],
          "answers": [{"status": 200}]}]
        """));

    // When
    BinRule failed = BinMatcher.first(rules, call("POST", "/payments/hook", Map.of(), "{\"data\":{\"object\":{\"status\":\"failed\"}}}")).orElseThrow();
    BinRule paid = BinMatcher.first(rules, call("POST", "/payments/hook", Map.of(), "{\"data\":{\"object\":{\"status\":\"paid\"}}}")).orElseThrow();

    // Then
    assertThat(failed.name()).isEqualTo("Failed payments");
    assertThat(paid.name()).isEqualTo("Any payment");
    assertThat(BinMatcher.first(rules, call("GET", "/payments/hook", Map.of(), ""))).isEmpty();
  }

  @Test
  void conditionsCheckQueryHeadersBodyAndPatterns() {
    // Given
    BinMatcher.Call c = call("POST", "/orders/42", Map.of("X-GitHub-Event", "push"), "{\"items\":[{\"sku\":\"a-1\"}]}");

    // When / Then
    assertThat(BinMatcher.holds(new BinRule.Condition(BinRule.Target.QUERY, "attempt", BinRule.Op.EQUALS, "2"), c, c.json())).isTrue();
    assertThat(BinMatcher.holds(new BinRule.Condition(BinRule.Target.HEADER, "x-github-event", BinRule.Op.EQUALS, "push"), c, c.json())).isTrue();
    assertThat(BinMatcher.holds(new BinRule.Condition(BinRule.Target.BODY, "", BinRule.Op.CONTAINS, "a-1"), c, c.json())).isTrue();
    assertThat(BinMatcher.holds(new BinRule.Condition(BinRule.Target.PATH, "", BinRule.Op.MATCHES, "^/orders/\\d+$"), c, c.json())).isTrue();
    assertThat(BinMatcher.holds(new BinRule.Condition(BinRule.Target.JSON, "$.items[0].sku", BinRule.Op.EQUALS, "a-1"), c, c.json())).isTrue();
    assertThat(BinMatcher.holds(new BinRule.Condition(BinRule.Target.JSON, "$.missing", BinRule.Op.EXISTS, ""), c, c.json())).isFalse();
    assertThat(BinMatcher.holds(new BinRule.Condition(BinRule.Target.HEADER, "X-Missing", BinRule.Op.EXISTS, ""), c, c.json())).isFalse();
  }

  private static boolean holds(BinMatcher.Call c, BinRule.Target target, String key, BinRule.Op op, String value) {
    return BinMatcher.holds(new BinRule.Condition(target, key, op, value), c, c.json());
  }

  @Test
  void negativeTestsTreatAMissingValueAsNotEqualAndNotContaining() {
    // Given
    BinMatcher.Call c = call("POST", "/orders/42", Map.of("X-Event", "order.created"), "{\"status\":\"paid\",\"note\":null}");

    // When / Then
    assertThat(holds(c, BinRule.Target.HEADER, "X-Event", BinRule.Op.NOT_EQUALS, "order.deleted")).isTrue();
    assertThat(holds(c, BinRule.Target.HEADER, "X-Event", BinRule.Op.NOT_EQUALS, "order.created")).isFalse();
    assertThat(holds(c, BinRule.Target.PATH, "", BinRule.Op.NOT_CONTAINS, "/refunds")).isTrue();
    assertThat(holds(c, BinRule.Target.PATH, "", BinRule.Op.NOT_CONTAINS, "/orders")).isFalse();
    assertThat(holds(c, BinRule.Target.QUERY, "missing", BinRule.Op.NOT_EQUALS, "x")).isTrue();
    assertThat(holds(c, BinRule.Target.QUERY, "missing", BinRule.Op.ABSENT, "")).isTrue();
    assertThat(holds(c, BinRule.Target.JSON, "$.note", BinRule.Op.ABSENT, "")).isTrue();
    assertThat(holds(c, BinRule.Target.JSON, "$.status", BinRule.Op.ABSENT, "")).isFalse();
  }

  @Test
  void aPathPatternMatchesEachParameterAndHandsItToTemplates() {
    // Given
    BinRule rule = rule("""
        {"method": "GET", "conditions": [{"target": "path", "op": "template", "value": "/users/:id/orders/{orderId}"}], "answers": [{"status": 200}]}
        """);

    // When
    boolean hit = BinMatcher.first(List.of(rule), call("GET", "/users/7/orders/ord_9", Map.of(), "")).isPresent();
    boolean tooLong = BinMatcher.first(List.of(rule), call("GET", "/users/7/orders/ord_9/items", Map.of(), "")).isPresent();
    boolean empty = BinMatcher.first(List.of(rule), call("GET", "/users//orders/ord_9", Map.of(), "")).isPresent();
    Map<String, String> params = BinMatcher.pathParams(rule, "/users/7/orders/ord_9/");

    // Then
    assertThat(hit).isTrue();
    assertThat(tooLong).isFalse();
    assertThat(empty).isFalse();
    assertThat(params).containsExactly(Map.entry("id", "7"), Map.entry("orderId", "ord_9"));
  }

  @Test
  void readsFormFieldsXmlSoapActionsAndGraphqlOperations() {
    // Given
    BinMatcher.Call form = call("POST", "/hook", Map.of("Content-Type", "application/x-www-form-urlencoded"), "status=paid&amount=42%2E00&name=Ana+Lee");
    BinMatcher.Call xml = call("POST", "/soap", Map.of("Content-Type", "text/xml", "SOAPAction", "\"urn:Pay\""),
        "<Envelope><Body><Pay><order><id>ord_5</id></order></Pay></Body></Envelope>");
    BinMatcher.Call soap12 = call("POST", "/soap", Map.of("Content-Type", "application/soap+xml; charset=utf-8; action=\"urn:Refund\""), "<Envelope/>");
    BinMatcher.Call named = call("POST", "/graphql", Map.of(), "{\"query\":\"query GetOrder($id: ID!) { order(id: $id) { id } }\"}");
    BinMatcher.Call byName = call("POST", "/graphql", Map.of(), "{\"operationName\":\"CancelOrder\",\"query\":\"mutation CancelOrder { cancel }\"}");
    BinMatcher.Call anonymous = call("POST", "/graphql", Map.of(), "{\"query\":\"{ products { id } }\"}");

    // When / Then
    assertThat(holds(form, BinRule.Target.FORM, "amount", BinRule.Op.EQUALS, "42.00")).isTrue();
    assertThat(holds(form, BinRule.Target.FORM, "name", BinRule.Op.EQUALS, "Ana Lee")).isTrue();
    assertThat(holds(form, BinRule.Target.FORM, "missing", BinRule.Op.ABSENT, "")).isTrue();
    assertThat(holds(xml, BinRule.Target.XML, "//order/id", BinRule.Op.EQUALS, "ord_5")).isTrue();
    assertThat(holds(xml, BinRule.Target.XML, "/Envelope/Body/Pay/order/id", BinRule.Op.STARTS_WITH, "ord_")).isTrue();
    assertThat(holds(xml, BinRule.Target.SOAP_ACTION, "", BinRule.Op.EQUALS, "urn:Pay")).isTrue();
    assertThat(holds(soap12, BinRule.Target.SOAP_ACTION, "", BinRule.Op.EQUALS, "urn:Refund")).isTrue();
    assertThat(holds(named, BinRule.Target.GRAPHQL, "", BinRule.Op.EQUALS, "GetOrder")).isTrue();
    assertThat(holds(byName, BinRule.Target.GRAPHQL, "", BinRule.Op.EQUALS, "CancelOrder")).isTrue();
    assertThat(holds(anonymous, BinRule.Target.GRAPHQL, "", BinRule.Op.EQUALS, "products")).isTrue();
    assertThat(holds(form, BinRule.Target.XML, "//id", BinRule.Op.EXISTS, "")).isFalse();
  }

  @Test
  void anXmlBodyCantReachOutsideTheRequest() {
    // Given
    BinMatcher.Call c = call("POST", "/soap", Map.of(), "<!DOCTYPE x [<!ENTITY e SYSTEM \"file:///etc/passwd\">]><x>&e;</x>");

    // When
    boolean seen = holds(c, BinRule.Target.XML, "/x", BinRule.Op.CONTAINS, "root");

    // Then
    assertThat(seen).isFalse();
  }

  @Test
  void acceptsTheNewMethodsAndRefusesPatternsAnywhereButThePath() {
    // Given
    String query = "{\"method\": \"QUERY\", \"answers\": [{\"status\": 200}]}";

    // When
    BinRule rule = rule(query);

    // Then
    assertThat(rule.method()).isEqualTo("QUERY");
    assertThat(rule("{\"method\": \"TRACE\", \"answers\": [{\"status\": 200}]}").method()).isEqualTo("TRACE");
    assertThatThrownBy(() -> rule("{\"conditions\": [{\"target\": \"header\", \"key\": \"X\", \"op\": \"template\", \"value\": \"/a/:b\"}], \"answers\": [{\"status\": 200}]}"))
        .hasMessageContaining("Only the path");
    assertThatThrownBy(() -> rule("{\"conditions\": [{\"target\": \"path\", \"op\": \"template\", \"value\": \"/a/:b-c\"}], \"answers\": [{\"status\": 200}]}"))
        .hasMessageContaining("letters, digits");
    assertThatThrownBy(() -> rule("{\"conditions\": [{\"target\": \"xml\", \"key\": \"//[\", \"op\": \"exists\"}], \"answers\": [{\"status\": 200}]}"))
        .hasMessageContaining("XPath");
    assertThat(rule("{\"conditions\": [{\"target\": \"soap-action\", \"op\": \"absent\", \"value\": \"ignored\"}], \"answers\": [{\"status\": 200}]}")
        .conditions().get(0)).isEqualTo(new BinRule.Condition(BinRule.Target.SOAP_ACTION, "", BinRule.Op.ABSENT, ""));
  }

  @Test
  void aSlowPatternGivesUpInsteadOfHangingTheServer() {
    // Given
    String evil = "a".repeat(40) + "!";

    // When
    long started = System.nanoTime();
    boolean matched = BinMatcher.matches("^(a+)+$", evil);

    // Then
    assertThat(matched).isFalse();
    assertThat(System.nanoTime() - started).isLessThan(2_000_000_000L);
  }

  @Test
  void findsTheWebhookEventType() {
    // Given
    BinMatcher.Call github = call("POST", "/", Map.of("X-GitHub-Event", "pull_request"), "{\"action\":\"opened\"}");
    BinMatcher.Call stripe = call("POST", "/", Map.of(), "{\"type\":\"invoice.paid\",\"id\":\"evt_1\"}");
    BinMatcher.Call plain = call("POST", "/", Map.of(), "{\"amount\":4}");

    // When / Then
    assertThat(BinMatcher.eventOf(github.request(), github.json())).isEqualTo("pull_request");
    assertThat(BinMatcher.eventOf(stripe.request(), stripe.json())).isEqualTo("invoice.paid");
    assertThat(BinMatcher.eventOf(plain.request(), plain.json())).isEmpty();
  }

  @Test
  void refusesRulesThatCantWork() {
    // When / Then
    assertThatThrownBy(() -> rule("{\"answers\": []}")).hasMessageContaining("Give the rule an answer");
    assertThatThrownBy(() -> rule("{\"answers\": [{\"status\": 99}]}")).hasMessageContaining("100 to 599");
    assertThatThrownBy(() -> rule("{\"answers\": [{\"delayMs\": 20000}]}")).hasMessageContaining("15 seconds");
    assertThatThrownBy(() -> rule("{\"conditions\": [{\"target\": \"json\", \"key\": \"event\"}], \"answers\": [{}]}")).hasMessageContaining("start with $");
    assertThatThrownBy(() -> rule("{\"conditions\": [{\"op\": \"matches\", \"value\": \"(\"}], \"answers\": [{}]}")).hasMessageContaining("regular expression");
    assertThatThrownBy(() -> BinRule.listFrom(json("[{\"answers\": [{}]}, {\"method\": \"FETCH\", \"answers\": [{}]}]"))).hasMessageStartingWith("Rule 2:");
  }

  @Test
  void aRuleSurvivesARoundTripThroughJson() {
    // Given
    BinRule original = rule("""
        {"id": "r1", "name": "Slow", "method": "POST", "conditions": [{"target": "header", "key": "X-Retry", "op": "exists"}],
         "answers": [{"weight": 3, "status": 200, "delayMs": 5000, "headers": {"X-A": "1"}, "body": "ok", "template": true},
                     {"weight": 1, "status": 503, "body": "down"}]}
        """);

    // When
    BinRule copy = BinRule.from(original.toJson());

    // Then
    assertThat(copy).isEqualTo(original);
  }
}
