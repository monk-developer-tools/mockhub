package io.mockhub.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StubControlsIntegrationTest {

  private static final String API = "/__admin/mockhub/api";

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
  }

  @AfterEach
  void stop() {
    server.close();
  }

  private String stub(String json) {
    return api.post(API + "/projects/" + projectId + "/stubs", json).json().get("id").asText();
  }

  @Test
  void aDisabledStubStopsAnsweringUntilItIsEnabledAgain() {
    // Given
    String id = stub("{\"request\":{\"method\":\"GET\",\"urlPath\":\"/status\"},\"response\":{\"status\":200,\"body\":\"up\"}}");

    // When
    JsonNode disabled = api.put(API + "/projects/" + projectId + "/stubs/" + id + "/enabled", "{\"enabled\":false}").json();
    int whileDisabled = api.get("/pay/status").status();
    api.put(API + "/projects/" + projectId + "/stubs/" + id + "/enabled", "{\"enabled\":true}");
    ApiClient.Result whileEnabled = api.get("/pay/status");

    // Then
    assertThat(disabled.at("/metadata/mockhub/disabled").asBoolean()).isTrue();
    assertThat(disabled.at("/request").has("customMatcher")).isFalse();
    assertThat(whileDisabled).isEqualTo(404);
    assertThat(whileEnabled.status()).isEqualTo(200);
    assertThat(whileEnabled.body()).isEqualTo("up");
  }

  @Test
  void aDisabledStubStaysDisabledAfterItIsEditedAndAfterARestart() {
    // Given
    String id = stub("{\"request\":{\"method\":\"GET\",\"urlPath\":\"/status\"},\"response\":{\"status\":200}}");
    JsonNode disabled = api.put(API + "/projects/" + projectId + "/stubs/" + id + "/enabled", "{\"enabled\":false}").json();

    // When
    ((com.fasterxml.jackson.databind.node.ObjectNode) disabled).put("name", "Status");
    api.put(API + "/projects/" + projectId + "/stubs/" + id, disabled.toString());
    server.close();
    server = MockHubServer.start(data);
    api = server.api();

    // Then
    assertThat(api.get("/pay/status").status()).isEqualTo(404);
    assertThat(api.get(API + "/projects/" + projectId + "/stubs/" + id).json().get("name").asText()).isEqualTo("Status");
  }

  @Test
  void countsCallsPerStub() {
    // Given
    String a = stub("{\"request\":{\"method\":\"GET\",\"urlPath\":\"/a\"},\"response\":{\"status\":200}}");
    String b = stub("{\"request\":{\"method\":\"GET\",\"urlPath\":\"/b\"},\"response\":{\"status\":200}}");
    api.get("/pay/a");
    api.get("/pay/a");
    api.get("/pay/missing");

    // When
    JsonNode usage = api.get(API + "/projects/" + projectId + "/usage").json();

    // Then
    assertThat(usage.path(a).path("count").asInt()).isEqualTo(2);
    assertThat(usage.path(a).path("lastCalledAt").asText()).isNotEmpty();
    assertThat(usage.has(b)).isFalse();
  }

  @Test
  void anErrorRateOf100PercentAlwaysFails() {
    // Given
    stub("{\"request\":{\"method\":\"GET\",\"urlPath\":\"/flaky\"},\"response\":{\"status\":200,\"body\":\"ok\"},"
        + "\"metadata\":{\"mockhub\":{\"errorRate\":{\"percent\":100,\"status\":503}}}}");

    // When
    ApiClient.Result response = api.get("/pay/flaky");

    // Then
    assertThat(response.status()).isEqualTo(503);
    assertThat(response.simulatedError()).isEqualTo("true");
    assertThat(response.json().get("error").asText()).isEqualTo("Simulated failure");
  }

  @Test
  void anErrorRateOfZeroNeverFails() {
    // Given
    stub("{\"request\":{\"method\":\"GET\",\"urlPath\":\"/fine\"},\"response\":{\"status\":200,\"body\":\"ok\"},"
        + "\"metadata\":{\"mockhub\":{\"errorRate\":{\"percent\":0,\"status\":503}}}}");

    // When
    ApiClient.Result response = api.get("/pay/fine");

    // Then
    assertThat(response.status()).isEqualTo(200);
    assertThat(response.simulatedError()).isEmpty();
  }

  @Test
  void aSoapStubFailsWithASoapFault() {
    // Given
    stub("{\"request\":{\"method\":\"POST\",\"urlPath\":\"/ws\"},\"response\":{\"status\":200,\"body\":\"<ok/>\"},"
        + "\"metadata\":{\"mockhub\":{\"type\":\"soap\",\"errorRate\":{\"percent\":100}}}}");

    // When
    ApiClient.Result response = api.postXml("/pay/ws", "<x/>");

    // Then
    assertThat(response.status()).isEqualTo(500);
    assertThat(response.contentType()).startsWith("text/xml");
    assertThat(response.body()).contains("<faultstring>Simulated failure (500)</faultstring>");
  }

  @Test
  void aProjectApiKeyIsRequiredOnEveryMockCall() {
    // Given
    stub("{\"request\":{\"method\":\"GET\",\"urlPath\":\"/balance\"},\"response\":{\"status\":200,\"body\":\"42\"}}");
    JsonNode project = api.put(API + "/projects/" + projectId, "{\"apiKey\":{\"header\":\"X-Api-Key\",\"value\":\"s3cret\"}}").json();

    // When
    ApiClient.Result missing = api.get("/pay/balance");
    ApiClient.Result wrong = api.send("GET", "/pay/balance", null, null, Map.of("X-Api-Key", "nope"));
    ApiClient.Result right = api.send("GET", "/pay/balance", null, null, Map.of("X-Api-Key", "s3cret"));
    ApiClient.Result preflight = api.send("OPTIONS", "/pay/balance", null, null, Map.of());

    // Then
    assertThat(project.at("/apiKey/header").asText()).isEqualTo("X-Api-Key");
    assertThat(missing.status()).isEqualTo(401);
    assertThat(missing.json().get("error").asText()).isEqualTo("Missing API key. Send it in the X-Api-Key header.");
    assertThat(wrong.status()).isEqualTo(401);
    assertThat(wrong.json().get("error").asText()).startsWith("Invalid API key");
    assertThat(right.status()).isEqualTo(200);
    assertThat(right.body()).isEqualTo("42");
    assertThat(preflight.status()).isNotEqualTo(401);
  }

  @Test
  void soapCallsWithoutTheKeyGetASoapFaultAndTheWsdlStaysOpen() {
    // Given
    stub("{\"request\":{\"method\":\"GET\",\"url\":\"/ws?wsdl\"},\"response\":{\"status\":200,\"body\":\"<definitions/>\"}}");
    api.put(API + "/projects/" + projectId, "{\"apiKey\":{\"value\":\"s3cret\"}}");

    // When
    ApiClient.Result call = api.postXml("/pay/ws", "<x/>");
    ApiClient.Result wsdl = api.get("/pay/ws?wsdl");

    // Then
    assertThat(call.status()).isEqualTo(401);
    assertThat(call.body()).contains("<faultcode>soap:Client</faultcode>").contains("X-API-Key");
    assertThat(wsdl.status()).isEqualTo(200);
  }

  @Test
  void otherProjectsAndTurningTheKeyOffAreUnaffected() {
    // Given
    String other = api.post(API + "/projects", "{\"name\":\"Open\",\"slug\":\"open\"}").json().get("id").asText();
    api.post(API + "/projects/" + other + "/stubs", "{\"request\":{\"method\":\"GET\",\"urlPath\":\"/x\"},\"response\":{\"status\":200}}");
    stub("{\"request\":{\"method\":\"GET\",\"urlPath\":\"/x\"},\"response\":{\"status\":200}}");
    api.put(API + "/projects/" + projectId, "{\"apiKey\":{\"value\":\"s3cret\"}}");

    // When
    int open = api.get("/open/x").status();
    int locked = api.get("/pay/x").status();
    JsonNode off = api.put(API + "/projects/" + projectId, "{\"apiKey\":null}").json();
    int unlocked = api.get("/pay/x").status();

    // Then
    assertThat(open).isEqualTo(200);
    assertThat(locked).isEqualTo(401);
    assertThat(off.get("apiKey").isNull()).isTrue();
    assertThat(unlocked).isEqualTo(200);
  }

  @Test
  void exportedCollectionsSendTheApiKey() {
    // Given
    stub("{\"request\":{\"method\":\"GET\",\"urlPath\":\"/x\"},\"response\":{\"status\":200}}");
    api.put(API + "/projects/" + projectId, "{\"apiKey\":{\"value\":\"s3cret\"}}");

    // When
    String postman = api.get(API + "/projects/" + projectId + "/export/postman").body();

    // Then
    assertThat(postman).contains("X-API-Key").contains("s3cret");
  }
}
