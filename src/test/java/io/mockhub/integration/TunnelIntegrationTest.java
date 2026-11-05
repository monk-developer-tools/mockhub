package io.mockhub.integration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.tomakehurst.wiremock.WireMockServer;
import io.mockhub.tunnel.TunnelClient;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TunnelIntegrationTest {

  private static final String API = "/__admin/mockhub/api";

  @TempDir
  Path data;
  private MockHubServer server;
  private ApiClient admin;
  private WireMockServer laptop;
  private TunnelClient client;
  private CompletableFuture<String> clientResult;
  private final ByteArrayOutputStream clientLog = new ByteArrayOutputStream();
  private String projectId;
  private String tokenId;

  @BeforeEach
  void start() {
    server = MockHubServer.start(data, Map.of("MOCKHUB_USERS", "admin:pw:admin,bob:pw:admin"));
    admin = server.api().login("admin", "pw");
    projectId = admin.post(API + "/projects", "{\"name\":\"Payments\",\"slug\":\"pay\"}").json().get("id").asText();
    laptop = new WireMockServer(options().dynamicPort());
    laptop.start();
    laptop.stubFor(get(urlPathEqualTo("/api/orders/7")).willReturn(aResponse().withStatus(200)
        .withHeader("Content-Type", "application/json").withHeader("X-Laptop", "yes").withBody("{\"id\":7,\"from\":\"laptop\"}")));
    laptop.stubFor(any(urlEqualTo("/hooks")).willReturn(aResponse().withStatus(204)));
  }

  @AfterEach
  void stop() throws Exception {
    if (client != null) client.stop();
    server.close();
    if (client != null) clientResult.get(60, java.util.concurrent.TimeUnit.SECONDS);
    laptop.stop();
  }

  private String token() {
    ApiClient.Result created = admin.post(API + "/auth/tokens", "{\"name\":\"laptop\",\"role\":\"editor\",\"expiresInDays\":30}");
    assertThat(created.status()).as(created.body()).isEqualTo(200);
    tokenId = created.json().get("id").asText();
    return created.json().get("secret").asText();
  }

  private void connect(String name) {
    TunnelClient.Settings settings = new TunnelClient.Settings(server.baseUrl(), token(), name, "http://localhost:" + laptop.port(), 2);
    client = new TunnelClient(settings, HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(5)).build(),
        new PrintStream(clientLog, true, StandardCharsets.UTF_8));
    clientResult = CompletableFuture.supplyAsync(client::run);
    waitUntil(() -> tunnels().size() == 1 && tunnels().get(0).get("online").asBoolean());
  }

  private JsonNode tunnels() {
    return admin.get(API + "/tunnels").json();
  }

  private void stub(String json) {
    ApiClient.Result r = admin.post(API + "/projects/" + projectId + "/stubs", json);
    assertThat(r.status()).as(r.body()).isEqualTo(201);
  }

  private static void waitUntil(BooleanSupplier condition) {
    Instant deadline = Instant.now().plusSeconds(15);
    while (!condition.getAsBoolean()) {
      if (Instant.now().isAfter(deadline)) throw new AssertionError("Timed out waiting");
      try {
        Thread.sleep(100);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return;
      }
    }
  }

  @Test
  void aProxyStubAnswersFromTheServiceBehindTheTunnel() {
    // Given
    connect("laptop");
    stub("{\"request\":{\"method\":\"GET\",\"urlPathPattern\":\"/orders/.*\"},\"response\":{\"proxyBaseUrl\":\"tunnel://laptop/api\"}}");

    // When
    ApiClient.Result r = admin.get("/pay/orders/7?expand=items");

    // Then
    assertThat(r.status()).as(r.body()).isEqualTo(200);
    assertThat(r.body()).isEqualTo("{\"id\":7,\"from\":\"laptop\"}");
    assertThat(r.header("X-Laptop")).isEqualTo("yes");
    laptop.verify(com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor(urlEqualTo("/api/orders/7?expand=items")));
    assertThat(tunnels().get(0).get("requests").asInt()).isEqualTo(1);
    assertThat(clientLog.toString(StandardCharsets.UTF_8)).contains("GET /api/orders/7?expand=items -> 200");
  }

  @Test
  void aCalloutReachesTheServiceBehindTheTunnel() {
    // Given
    connect("laptop");
    stub("{\"request\":{\"method\":\"POST\",\"urlPath\":\"/charges\"},\"response\":{\"status\":201},"
        + "\"serveEventListeners\":[{\"name\":\"mockhub-callout\",\"parameters\":{\"method\":\"POST\",\"url\":\"tunnel://laptop/hooks\",\"body\":\"{\\\"event\\\":\\\"charge.created\\\"}\"}}]}");

    // When
    ApiClient.Result r = admin.post("/pay/charges", "{}");

    // Then
    assertThat(r.status()).isEqualTo(201);
    waitUntil(() -> !laptop.findAll(postRequestedFor(urlEqualTo("/hooks"))).isEmpty());
    laptop.verify(postRequestedFor(urlEqualTo("/hooks")).withRequestBody(equalTo("{\"event\":\"charge.created\"}")));
  }

  @Test
  void aTunnelThatIsNotConnectedAnswers502() {
    // Given
    stub("{\"request\":{\"method\":\"GET\",\"urlPath\":\"/orders/1\"},\"response\":{\"proxyBaseUrl\":\"tunnel://nobody\"}}");

    // When
    ApiClient.Result r = admin.get("/pay/orders/1");

    // Then
    assertThat(r.status()).isEqualTo(502);
    assertThat(r.header("X-MockHub-Tunnel-Error")).contains("Tunnel nobody isn't connected");
  }

  @Test
  void revokingTheTokenClosesTheTunnelAndStopsTheClient() throws Exception {
    // Given
    connect("laptop");

    // When
    admin.delete(API + "/auth/tokens/" + tokenId);

    // Then
    String error = clientResult.get(60, java.util.concurrent.TimeUnit.SECONDS);
    client = null;
    assertThat(error).contains("MockHub refused the tunnel");
    assertThat(tunnels()).isEmpty();
  }

  @Test
  void aNameInUseByAnotherUserIsRefused() {
    // Given
    connect("laptop");
    ApiClient bob = server.api().login("bob", "pw");
    ApiClient.Result created = bob.post(API + "/auth/tokens", "{\"name\":\"bob\",\"role\":\"editor\",\"expiresInDays\":7}");
    assertThat(created.status()).as(created.body()).isEqualTo(200);
    String bobToken = created.json().get("secret").asText();

    // When
    ApiClient.Result taken = server.api().bearer(bobToken).send("POST", "/__admin/mockhub/tunnel/laptop/next", "", "application/json", Map.of());

    // Then
    assertThat(taken.status()).isEqualTo(409);
    assertThat(taken.json().get("error").asText()).contains("in use by admin");
  }
}
