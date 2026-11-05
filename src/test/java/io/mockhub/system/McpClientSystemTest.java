package io.mockhub.system;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class McpClientSystemTest {

  private static final String API = "/__admin/mockhub/api";
  private static final ObjectMapper JSON = new ObjectMapper();
  private static final String BASIC = "Basic " + Base64.getEncoder().encodeToString("lead:s3cret".getBytes(StandardCharsets.UTF_8));

  @TempDir
  Path data;
  private final HttpClient http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(java.time.Duration.ofSeconds(10)).build();

  @Test
  void anMcpClientManagesMocksWithAnAccessToken() throws Exception {
    try (MockHubProcess hub = MockHubProcess.start(data, Map.of("MOCKHUB_USERS", "lead:s3cret:admin"))) {
      // Given
      post(hub, API + "/projects", "{\"name\":\"Orders\",\"slug\":\"orders\"}");
      String token = JSON.readTree(post(hub, API + "/auth/tokens", "{\"name\":\"Agent\",\"role\":\"editor\"}").body()).get("secret").asText();
      HttpClientStreamableHttpTransport transport = HttpClientStreamableHttpTransport.builder(hub.baseUrl())
          .endpoint("/__admin/mockhub/mcp")
          .addHttpRequestCustomizer((request, method, uri, body, context) -> request.header("Authorization", "Bearer " + token))
          .build();

      try (McpSyncClient client = McpClient.sync(transport).requestTimeout(Duration.ofSeconds(20))
          .jsonSchemaValidator((schema, content) -> io.modelcontextprotocol.json.schema.JsonSchemaValidator.ValidationResponse.asValid(null)).build()) {
        // When
        McpSchema.InitializeResult init = client.initialize();
        List<String> tools = client.listTools().tools().stream().map(McpSchema.Tool::name).toList();
        McpSchema.CallToolResult created = client.callTool(call("create_stub", Map.of(
            "project", "orders",
            "stub", Map.of("name", "Get order", "request", Map.of("method", "GET", "urlPathTemplate", "/orders/{id}"),
                "response", Map.of("status", 200, "jsonBody", Map.of("status", "SHIPPED"))))));
        HttpResponse<String> mock = http.send(HttpRequest.newBuilder(URI.create(hub.baseUrl() + "/orders/orders/42")).build(),
            HttpResponse.BodyHandlers.ofString());
        McpSchema.CallToolResult listed = client.callTool(call("list_stubs", Map.of("project", "Orders")));

        // Then
        assertThat(init.serverInfo().name()).isEqualTo("mockhub");
        assertThat(tools).contains("list_projects", "create_stub", "update_stub", "search_requests", "set_runtime", "reorder_stubs");
        assertThat(created.isError()).as(text(created)).isFalse();
        assertThat(mock.statusCode()).isEqualTo(200);
        assertThat(mock.body()).contains("SHIPPED");
        JsonNode stubs = JSON.readTree(text(listed));
        assertThat(stubs).singleElement().satisfies(s -> assertThat(s.get("name").asText()).isEqualTo("Get order"));
      }
    }
  }

  private HttpResponse<String> post(MockHubProcess hub, String path, String body) throws Exception {
    return http.send(HttpRequest.newBuilder(URI.create(hub.baseUrl() + path))
        .header("Authorization", BASIC).header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
  }

  private static McpSchema.CallToolRequest call(String name, Map<String, Object> arguments) {
    return McpSchema.CallToolRequest.builder(name).arguments(arguments).build();
  }

  private static String text(McpSchema.CallToolResult result) {
    return ((McpSchema.TextContent) result.content().get(0)).text();
  }
}
