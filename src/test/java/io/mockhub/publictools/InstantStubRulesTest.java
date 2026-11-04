package io.mockhub.publictools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.mockhub.support.Fixtures;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

class InstantStubRulesTest {

  @Test
  void acceptsAPlainStubAndSwapsTemplatingForTheSandbox() {
    // Given
    ObjectNode stub = Fixtures.json("""
        {"id": "x", "name": "Order", "request": {"method": "GET", "urlPathTemplate": "/orders/{id}",
         "headers": {"Accept": {"contains": "json"}}},
         "response": {"status": 200, "body": "{{request.path.id}}", "transformers": ["response-template", "mockhub-weighted"],
         "fixedDelayMilliseconds": 500}}""");

    // When
    ObjectNode checked = InstantStubRules.check(stub);

    // Then
    assertThat(checked.has("id")).isFalse();
    assertThat(checked.at("/response/transformers").toString()).isEqualTo("[\"mockhub-weighted\",\"mockhub-public-template\"]");
    assertThat(InstantStubRules.forDisplay(checked).at("/response/transformers").toString())
        .isEqualTo("[\"mockhub-weighted\",\"response-template\"]");
    ObjectNode stored = checked.deepCopy().put("id", "s1").put("uuid", "s1").put("persistent", true);
    stored.putObject("metadata").put("mockhub", "x");
    assertThat(InstantStubRules.forDisplay(stored).properties().stream().map(java.util.Map.Entry::getKey))
        .containsExactly("name", "request", "response", "id");
  }

  @ParameterizedTest
  @CsvSource(delimiter = '|', value = {
      "{\"request\":{\"url\":\"/a\"},\"response\":{\"proxyBaseUrl\":\"http://169.254.169.254\"}} | response.proxyBaseUrl",
      "{\"request\":{\"url\":\"/a\"},\"response\":{\"bodyFileName\":\"../../etc/passwd\"}} | response.bodyFileName",
      "{\"request\":{\"url\":\"/a\"},\"response\":{},\"serveEventListeners\":[{\"name\":\"webhook\"}]} | serveEventListeners",
      "{\"request\":{\"urlPattern\":\"/(a+)+$\"},\"response\":{}} | request.urlPattern",
      "{\"request\":{\"url\":\"/a\",\"headers\":{\"X\":{\"matches\":\"(a+)+$\"}}},\"response\":{}} | matches",
      "{\"request\":{\"url\":\"/a\",\"bodyPatterns\":[{\"matchesXPath\":\"//a\"}]},\"response\":{}} | matchesXPath",
      "{\"request\":{\"url\":\"/a\",\"bodyPatterns\":[{\"matchesJsonPath\":\"$[?(@.a =~ /x/)]\"}]},\"response\":{}} | =~",
      "{\"request\":{\"url\":\"/a\"},\"response\":{\"fixedDelayMilliseconds\":60000}} | 10,000 ms",
      "{\"request\":{\"url\":\"/a\"},\"response\":{\"delayDistribution\":{\"type\":\"lognormal\",\"median\":10}}} | uniform",
      "{\"request\":{\"url\":\"/a\"},\"response\":{\"transformers\":[\"mockhub-crud\"]}} | CRUD resource isn't valid",
      "{\"request\":{\"url\":\"/a\"},\"response\":{\"transformers\":[\"mockhub-crud\",\"response-template\"],\"transformerParameters\":{\"resource\":\"users\"}}} | other transformers",
      "{\"request\":{\"url\":\"/a\",\"bodyPatterns\":[{\"matchesXPath\":\"//*[local-name()=\\u0027Body\\u0027]/*[local-name()=\\u0027a\\u0027]/..\"}]},\"response\":{}} | matchesXPath",
      "{\"request\":{\"url\":\"/a\",\"queryParameters\":{\"q\":{\"matches\":\".*.*.*\"}}},\"response\":{}} | matches",
      "{\"request\":{\"url\":\"/a\"}} | needs a response",
  })
  void refusesWhatCouldReachOutReadFilesOrBurnCpu(String json, String message) {
    // Then
    assertThatThrownBy(() -> InstantStubRules.check(Fixtures.json(json)))
        .isInstanceOf(InstantStubRules.Refused.class)
        .hasMessageContaining(message);
  }

  @Test
  void refusesOversizedStubs() {
    // Given
    ObjectNode stub = Fixtures.json("{\"request\":{\"url\":\"/a\"},\"response\":{}}");
    ((ObjectNode) stub.get("response")).put("body", "x".repeat(InstantStubRules.MAX_STUB_BYTES));

    // Then
    assertThatThrownBy(() -> InstantStubRules.check(stub)).hasMessageContaining("64 KB");
  }

  @Test
  void rebuildsCrudStubsFromTheirParametersSoTheirPatternIsAlwaysOurs() {
    // Given
    ObjectNode stub = Fixtures.json("""
        {"name": "People", "request": {"method": "ANY", "urlPathPattern": "/(a+)+$"},
         "response": {"status": 200, "transformers": ["mockhub-crud"],
                      "transformerParameters": {"resource": "users", "idField": "id", "idType": "number"}}}""");

    // When
    ObjectNode checked = InstantStubRules.check(stub);

    // Then
    assertThat(checked.path("name").asText()).isEqualTo("People");
    assertThat(checked.at("/request/urlPathPattern").asText()).isEqualTo("/users(/[^/]+)?/?");
    assertThat(checked.at("/response/transformerParameters/resource").asText()).isEqualTo("users");
  }

  @Test
  void acceptsTheSoapBodyMatchAndAnyValueMatchThatTheWsdlImporterWrites() {
    // Given
    ObjectNode operation = Fixtures.json("""
        {"request": {"method": "POST", "urlPath": "/calc",
         "bodyPatterns": [{"matchesXPath": "//*[local-name()=\\u0027Body\\u0027]/*[local-name()=\\u0027Add\\u0027]"}]},
         "response": {"status": 200, "body": "<ok/>"}}""");
    ObjectNode wsdl = Fixtures.json("""
        {"request": {"method": "GET", "urlPath": "/calc", "queryParameters": {"wsdl": {"matches": ".*"}}},
         "response": {"status": 200, "body": "<definitions/>"}}""");

    // When
    ObjectNode checkedOperation = InstantStubRules.check(operation);
    ObjectNode checkedWsdl = InstantStubRules.check(wsdl);

    // Then
    assertThat(checkedOperation.at("/request/bodyPatterns/0/matchesXPath").asText()).contains("'Add'");
    assertThat(checkedWsdl.at("/request/queryParameters/wsdl/matches").asText()).isEqualTo(".*");
  }

  @Test
  void dropsImporterMetadataBeforeChecking() {
    // Given
    ObjectNode stub = Fixtures.json("""
        {"request": {"method": "GET", "urlPath": "/a"}, "response": {"status": 200},
         "metadata": {"mockhub": {"folder": "Pets"}}}""");

    // When
    ObjectNode checked = InstantStubRules.checkImported(stub);

    // Then
    assertThat(checked.has("metadata")).isFalse();
    assertThatThrownBy(() -> InstantStubRules.check(stub)).hasMessageContaining("metadata");
  }
}
