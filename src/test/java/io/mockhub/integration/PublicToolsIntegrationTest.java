package io.mockhub.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.Path;
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
}
