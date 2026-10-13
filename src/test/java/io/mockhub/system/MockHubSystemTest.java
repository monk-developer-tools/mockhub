package io.mockhub.system;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Base64;
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

  private HttpResponse<String> send(MockHubProcess hub, String method, String path, String body) throws IOException, InterruptedException {
    HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(hub.baseUrl() + path))
        .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body))
        .header("Authorization", AUTH);
    return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
  }

  private HttpResponse<String> sendAnonymously(MockHubProcess hub, String path) throws IOException, InterruptedException {
    return http.send(HttpRequest.newBuilder(URI.create(hub.baseUrl() + path)).build(), HttpResponse.BodyHandlers.ofString());
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
