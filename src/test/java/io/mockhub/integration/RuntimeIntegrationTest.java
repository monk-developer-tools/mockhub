package io.mockhub.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RuntimeIntegrationTest {

  private static final String API = "/__admin/mockhub/api";
  private static final String ORIGIN = "https://app.example";

  @TempDir
  Path data;
  private MockHubServer server;
  private ApiClient api;
  private String projectId;

  @BeforeEach
  void start() {
    server = MockHubServer.start(data);
    api = server.api();
    projectId = api.post(API + "/projects", "{\"name\":\"Pay\",\"slug\":\"pay\"}").json().get("id").asText();
    api.post(API + "/projects/" + projectId + "/stubs", "{\"request\":{\"method\":\"GET\",\"urlPath\":\"/status\"},\"response\":{\"status\":200,\"body\":\"up\"}}");
  }

  @AfterEach
  void stop() {
    server.close();
  }

  private ApiClient.Result runtime(String json) {
    return api.put(API + "/projects/" + projectId + "/runtime", json);
  }

  private ApiClient.Result call(String method, String path, Map<String, String> headers) {
    return api.send(method, path, null, null, headers);
  }

  @Test
  void aDefaultResponseAnswersUnmatchedCallsWithoutShowingUpAsAStub() {
    // Given
    runtime("{\"defaultResponse\":{\"status\":418,\"headers\":{\"X-Why\":\"default\"},\"body\":\"{\\\"path\\\":\\\"{{request.path}}\\\"}\",\"templated\":true}}");

    // When
    ApiClient.Result unmatched = api.get("/pay/nothing/here");
    ApiClient.Result matched = api.get("/pay/status");
    JsonNode stubs = api.get(API + "/projects/" + projectId + "/stubs").json();
    JsonNode project = api.get(API + "/projects/" + projectId).json();

    // Then
    assertThat(unmatched.status()).isEqualTo(418);
    assertThat(unmatched.header("X-Why")).isEqualTo("default");
    assertThat(unmatched.body()).isEqualTo("{\"path\":\"/pay/nothing/here\"}");
    assertThat(matched.body()).isEqualTo("up");
    assertThat(stubs).hasSize(1);
    assertThat(project.get("stubCount").asInt()).isEqualTo(1);
    assertThat(project.at("/runtime/defaultResponse/status").asInt()).isEqualTo(418);
  }

  @Test
  void aRootProjectsDefaultResponseLeavesOtherProjectsAlone() {
    // Given
    String rootId = api.post(API + "/projects", "{\"name\":\"Legacy\",\"slug\":\"legacy\",\"root\":true}").json().get("id").asText();
    api.put(API + "/projects/" + rootId + "/runtime", "{\"defaultResponse\":{\"status\":503,\"body\":\"legacy default\"}}");

    // When
    ApiClient.Result rootUnmatched = api.get("/orders/1");
    ApiClient.Result otherUnmatched = api.get("/pay/missing");

    // Then
    assertThat(rootUnmatched.status()).isEqualTo(503);
    assertThat(rootUnmatched.body()).isEqualTo("legacy default");
    assertThat(otherUnmatched.status()).isEqualTo(404);
  }

  @Test
  void removingTheDefaultResponseBringsBackThe404AndSettingsSurviveARestart() {
    // Given
    runtime("{\"defaultResponse\":{\"status\":200,\"body\":\"fallback\"},\"rateLimit\":{\"requests\":100,\"per\":\"minute\"}}");
    server.close();
    server = MockHubServer.start(data);
    api = server.api();
    int afterRestart = api.get("/pay/unknown").status();

    // When
    runtime("{\"rateLimit\":{\"requests\":100,\"per\":\"minute\"}}");

    // Then
    assertThat(afterRestart).isEqualTo(200);
    assertThat(api.get("/pay/unknown").status()).isEqualTo(404);
    assertThat(api.get(API + "/projects/" + projectId + "/runtime").json().at("/rateLimit/requests").asInt()).isEqualTo(100);
  }

  @Test
  void aProjectRateLimitAnswers429WithRetryHeaders() {
    // Given
    runtime("{\"rateLimit\":{\"requests\":2,\"per\":\"minute\"}}");

    // When
    int first = api.get("/pay/status").status();
    int second = api.get("/pay/status").status();
    ApiClient.Result third = api.get("/pay/status");

    // Then
    assertThat(first).isEqualTo(200);
    assertThat(second).isEqualTo(200);
    assertThat(third.status()).isEqualTo(429);
    assertThat(third.header("X-RateLimit-Limit")).isEqualTo("2");
    assertThat(third.header("X-RateLimit-Remaining")).isEqualTo("0");
    assertThat(Integer.parseInt(third.header("Retry-After"))).isBetween(1, 60);
    assertThat(third.json().get("error").asText()).contains("2 requests per minute");
  }

  @Test
  void aStubRateLimitOnlyLimitsThatStub() {
    // Given
    api.post(API + "/projects/" + projectId + "/stubs",
        "{\"request\":{\"method\":\"GET\",\"urlPath\":\"/search\"},\"response\":{\"status\":200},\"metadata\":{\"mockhub\":{\"rateLimit\":{\"requests\":1,\"per\":\"hour\"}}}}");

    // When
    int first = api.get("/pay/search").status();
    ApiClient.Result second = api.get("/pay/search");
    int other = api.get("/pay/status").status();

    // Then
    assertThat(first).isEqualTo(200);
    assertThat(second.status()).isEqualTo(429);
    assertThat(second.json().get("error").asText()).contains("1 request per hour for this stub");
    assertThat(other).isEqualTo(200);
  }

  @Test
  void aProjectDelayAppliesToStubsWithoutTheirOwn() {
    // Given
    runtime("{\"latency\":{\"mode\":\"fixed\",\"fixedMs\":400}}");

    // When
    long started = System.nanoTime();
    int status = api.get("/pay/status").status();
    long tookMs = (System.nanoTime() - started) / 1_000_000;

    // Then
    assertThat(status).isEqualTo(200);
    assertThat(tookMs).isGreaterThanOrEqualTo(400);
  }

  @Test
  void browsersGetCorsHeadersByDefault() {
    // Given
    Map<String, String> preflightHeaders = Map.of("Origin", ORIGIN, "Access-Control-Request-Method", "PUT", "Access-Control-Request-Headers", "x-trace");

    // When
    ApiClient.Result preflight = call("OPTIONS", "/pay/status", preflightHeaders);
    ApiClient.Result get = call("GET", "/pay/status", Map.of("Origin", ORIGIN));

    // Then
    assertThat(preflight.status()).isEqualTo(204);
    assertThat(preflight.header("Access-Control-Allow-Origin")).isEqualTo(ORIGIN);
    assertThat(preflight.header("Access-Control-Allow-Methods")).contains("PUT");
    assertThat(preflight.header("Access-Control-Allow-Headers")).isEqualTo("x-trace");
    assertThat(get.header("Access-Control-Allow-Origin")).isEqualTo(ORIGIN);
    assertThat(get.header("Access-Control-Allow-Credentials")).isEqualTo("true");
  }

  @Test
  void theDefaultResponseCarriesCorsHeadersSoBrowsersCanReadIt() {
    // Given
    runtime("{\"defaultResponse\":{\"status\":404,\"body\":\"no such endpoint\"}}");

    // When
    ApiClient.Result unmatched = call("GET", "/pay/missing", Map.of("Origin", ORIGIN));

    // Then
    assertThat(unmatched.status()).isEqualTo(404);
    assertThat(unmatched.header("Access-Control-Allow-Origin")).isEqualTo(ORIGIN);
  }

  @Test
  void rejectionsFromMockHubCarryCorsHeaders() {
    // Given
    runtime("{\"rateLimit\":{\"requests\":1,\"per\":\"hour\"}}");
    call("GET", "/pay/status", Map.of("Origin", ORIGIN));

    // When
    ApiClient.Result limited = call("GET", "/pay/status", Map.of("Origin", ORIGIN));

    // Then
    assertThat(limited.status()).isEqualTo(429);
    assertThat(limited.header("Access-Control-Allow-Origin")).isEqualTo(ORIGIN);
  }

  @Test
  void aMissingApiKeyAnswerCarriesCorsHeaders() {
    // Given
    api.put(API + "/projects/" + projectId, "{\"apiKey\":{\"header\":\"X-Api-Key\",\"value\":\"secret\"}}");

    // When
    ApiClient.Result missing = call("GET", "/pay/status", Map.of("Origin", ORIGIN));

    // Then
    assertThat(missing.status()).isEqualTo(401);
    assertThat(missing.header("Access-Control-Allow-Origin")).isEqualTo(ORIGIN);
  }

  @Test
  void aProjectCorsAllowListOnlyAnswersListedOrigins() {
    // Given
    runtime("{\"cors\":{\"origins\":[\"https://app.example\"],\"methods\":[\"GET\"],\"headers\":[\"Authorization\"]}}");

    // When
    ApiClient.Result allowed = call("OPTIONS", "/pay/status", Map.of("Origin", ORIGIN, "Access-Control-Request-Method", "GET"));
    ApiClient.Result other = call("GET", "/pay/status", Map.of("Origin", "https://evil.example"));

    // Then
    assertThat(allowed.header("Access-Control-Allow-Origin")).isEqualTo(ORIGIN);
    assertThat(allowed.header("Access-Control-Allow-Methods")).isEqualTo("GET");
    assertThat(allowed.header("Access-Control-Allow-Headers")).isEqualTo("Authorization");
    assertThat(allowed.header("Access-Control-Allow-Credentials")).isNull();
    assertThat(other.status()).isEqualTo(200);
    assertThat(other.header("Access-Control-Allow-Origin")).isNull();
  }

  @Test
  void simulatingACorsFailureLeavesTheHeadersOut() {
    // Given
    runtime("{\"cors\":{\"simulateFailure\":true}}");

    // When
    ApiClient.Result preflight = call("OPTIONS", "/pay/status", Map.of("Origin", ORIGIN, "Access-Control-Request-Method", "GET"));
    ApiClient.Result get = call("GET", "/pay/status", Map.of("Origin", ORIGIN));

    // Then
    assertThat(preflight.header("Access-Control-Allow-Origin")).isNull();
    assertThat(get.status()).isEqualTo(200);
    assertThat(get.header("Access-Control-Allow-Origin")).isNull();
  }

  @Test
  void anIpAllowListRefusesOtherAddresses() {
    // Given
    runtime("{\"ipAllowList\":[\"10.0.0.0/8\"]}");
    ApiClient.Result refused = api.get("/pay/status");

    // When
    runtime("{\"ipAllowList\":[\"10.0.0.0/8\",\"127.0.0.1\",\"::1\"]}");

    // Then
    assertThat(refused.status()).isEqualTo(403);
    assertThat(refused.json().get("error").asText()).contains("aren't allowed");
    assertThat(api.get("/pay/status").status()).isEqualTo(200);
  }

  @Test
  void rejectsSettingsThatCannotWork() {
    // When
    ApiClient.Result badCidr = runtime("{\"ipAllowList\":[\"10.0.0.0/33\"]}");
    ApiClient.Result badWindow = runtime("{\"rateLimit\":{\"requests\":5,\"per\":\"day\"}}");
    ApiClient.Result noOrigins = runtime("{\"cors\":{\"origins\":[]}}");
    ApiClient.Result backwards = runtime("{\"latency\":{\"mode\":\"uniform\",\"minMs\":500,\"maxMs\":100}}");

    // Then
    assertThat(badCidr.status()).isEqualTo(400);
    assertThat(badCidr.json().get("error").asText()).contains("10.0.0.0/33");
    assertThat(badWindow.status()).isEqualTo(400);
    assertThat(noOrigins.status()).isEqualTo(400);
    assertThat(backwards.status()).isEqualTo(400);
  }

  @Test
  void theWireMockExportKeepsTheDefaultResponseAsPlainWireMock() {
    // Given
    runtime("{\"defaultResponse\":{\"status\":404,\"body\":\"nope\"}}");

    // When
    JsonNode wiremock = api.get(API + "/projects/" + projectId + "/export/wiremock").json();
    JsonNode postman = api.get(API + "/projects/" + projectId + "/export/postman").json();

    // Then
    JsonNode fallback = wiremock.get("mappings").findParents("name").stream().filter(m -> m.get("name").asText().equals("Default response")).findFirst().orElseThrow();
    assertThat(fallback.at("/request/customMatcher").isMissingNode()).isTrue();
    assertThat(fallback.get("priority").asInt()).isEqualTo(1_000_000);
    assertThat(postman.toString()).doesNotContain("Default response");
  }
}
