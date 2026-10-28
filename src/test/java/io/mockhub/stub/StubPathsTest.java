package io.mockhub.stub;

import static io.mockhub.support.Fixtures.json;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class StubPathsTest {

  @ParameterizedTest
  @CsvSource({
      "url, /users/1, /pay/users/1",
      "url, /, /pay",
      "url, /search?q=x, /pay/search?q=x",
      "urlPath, users, /pay/users",
      "urlPathTemplate, /users/{id}, /pay/users/{id}",
      "urlPattern, /users/.*, /pay/users/.*",
      "urlPattern, ^/users/[0-9]+$, ^/pay/users/[0-9]+$",
      "urlPathPattern, .*, /pay/.*"
  })
  void prefixesEveryUrlMatcherWithTheProjectBasePath(String field, String relative, String absolute) {
    // Given
    ObjectNode stub = json("{'request':{'method':'GET','" + field + "':'" + relative + "'}}");

    // When
    StubPaths.toAbsolute(stub, "/pay");

    // Then
    assertThat(stub.at("/request/" + field).asText()).isEqualTo(absolute);
  }

  @ParameterizedTest
  @CsvSource({
      "url, /pay/users/1, /users/1",
      "url, /pay, /",
      "urlPathTemplate, /pay/users/{id}, /users/{id}",
      "urlPattern, ^/pay/users/.*, ^/users/.*",
      "urlPath, /other/users, /other/users"
  })
  void stripsTheBasePathWhenMakingRelative(String field, String absolute, String relative) {
    // Given
    ObjectNode stub = json("{'request':{'method':'GET','" + field + "':'" + absolute + "'}}");

    // When
    StubPaths.toRelative(stub, "/pay");

    // Then
    assertThat(stub.at("/request/" + field).asText()).isEqualTo(relative);
  }

  @Test
  void anyUrlInsideAProjectMatchesEverythingUnderItsBasePath() {
    // Given
    ObjectNode stub = json("{'request':{'method':'ANY'}}");

    // When
    StubPaths.toAbsolute(stub, "/pay");

    // Then
    assertThat(stub.at("/request/urlPattern").asText()).isEqualTo("/pay(/.*)?");
  }

  @Test
  void anyUrlRoundTripsBackToNoUrlMatcher() {
    // Given
    ObjectNode stub = json("{'request':{'method':'ANY'}}");
    StubPaths.toAbsolute(stub, "/pay");

    // When
    StubPaths.toRelative(stub, "/pay");

    // Then
    assertThat(stub.path("request").has("urlPattern")).isFalse();
  }

  @Test
  void createsARequestWhenTheStubHasNone() {
    // Given
    ObjectNode stub = json("{'response':{'status':200}}");

    // When
    StubPaths.toAbsolute(stub, "/pay");

    // Then
    assertThat(stub.at("/request/method").asText()).isEqualTo("ANY");
    assertThat(stub.at("/request/urlPattern").asText()).isEqualTo("/pay(/.*)?");
  }

  @Test
  void proxiedRequestsDropTheProjectBasePathByDefault() {
    // Given
    ObjectNode stub = json("{'request':{'urlPathPattern':'/.*'},'response':{'proxyBaseUrl':'https://api.example.com'}}");

    // When
    StubPaths.toAbsolute(stub, "/pay");

    // Then
    assertThat(stub.at("/response/proxyUrlPrefixToRemove").asText()).isEqualTo("/pay");
  }

  @Test
  void theDefaultProxyPrefixIsHiddenWhenMakingRelative() {
    // Given
    ObjectNode stub = json("{'request':{'url':'/pay/x'},'response':{'proxyBaseUrl':'https://a','proxyUrlPrefixToRemove':'/pay'}}");

    // When
    StubPaths.toRelative(stub, "/pay");

    // Then
    assertThat(stub.path("response").has("proxyUrlPrefixToRemove")).isFalse();
  }

  @Test
  void aCustomProxyPrefixIsKept() {
    // Given
    ObjectNode stub = json("{'request':{'url':'/x'},'response':{'proxyBaseUrl':'https://a','proxyUrlPrefixToRemove':'/pay/v1'}}");

    // When
    StubPaths.toAbsolute(stub, "/pay");
    StubPaths.toRelative(stub, "/pay");

    // Then
    assertThat(stub.at("/response/proxyUrlPrefixToRemove").asText()).isEqualTo("/pay/v1");
  }

  @ParameterizedTest
  @CsvSource({
      "url, /users/1",
      "urlPathPattern, /users/[0-9]+",
      "urlPattern, ^/users/.*"
  })
  void aProjectAtTheRootKeepsUrlsAsTheyAre(String field, String path) {
    // Given
    ObjectNode stub = json("{'request':{'method':'GET','" + field + "':'" + path + "'}}");

    // When
    StubPaths.toAbsolute(stub, "");
    String absolute = stub.at("/request/" + field).asText();
    StubPaths.toRelative(stub, "");

    // Then
    assertThat(absolute).isEqualTo(path);
    assertThat(stub.at("/request/" + field).asText()).isEqualTo(path);
  }

  @Test
  void aCatchAllAtTheRootMatchesEveryPath() {
    // Given
    ObjectNode stub = json("{'response':{'status':200}}");

    // When
    StubPaths.toAbsolute(stub, "");

    // Then
    assertThat(stub.at("/request/urlPattern").asText()).isEqualTo("/.*");
    StubPaths.toRelative(stub, "");
    assertThat(stub.path("request").has("urlPattern")).isFalse();
  }

  @Test
  void proxiesAtTheRootKeepTheWholePath() {
    // Given
    ObjectNode stub = json("{'request':{'urlPathPattern':'/.*'},'response':{'proxyBaseUrl':'https://api.example.com'}}");

    // When
    StubPaths.toAbsolute(stub, "");

    // Then
    assertThat(stub.path("response").has("proxyUrlPrefixToRemove")).isFalse();
  }
}
