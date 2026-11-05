package io.mockhub.integration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import java.io.ByteArrayInputStream;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.time.Duration;
import java.util.Map;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProxyIntegrationTest {

  private static final String API = "/__admin/mockhub/api";

  @TempDir
  Path data;
  private MockHubServer server;
  private ApiClient api;
  private WireMockServer partner;

  @AfterEach
  void stop() {
    if (server != null) server.close();
    if (partner != null) partner.stop();
  }

  private String start(Map<String, String> env) {
    server = MockHubServer.start(data, env);
    api = server.api();
    String id = api.post(API + "/projects", "{\"name\":\"Captured\",\"slug\":\"captured\"}").json().get("id").asText();
    ApiClient.Result stub = api.post(API + "/projects/" + id + "/stubs",
        "{\"request\":{\"method\":\"GET\",\"urlPath\":\"/hello\"},\"response\":{\"status\":200,\"body\":\"hi from the mock\"}}");
    assertThat(stub.status()).as(stub.body()).isEqualTo(201);
    return id;
  }

  private HttpClient client(SSLContext ssl) {
    HttpClient.Builder b = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(10))
        .proxy(ProxySelector.of(new InetSocketAddress("localhost", server.port())));
    if (ssl != null) b.sslContext(ssl);
    return b.build();
  }

  private static HttpResponse<String> call(HttpClient client, String url) throws Exception {
    return client.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(30)).build(), HttpResponse.BodyHandlers.ofString());
  }

  private SSLContext trustingTheProxyCa() throws Exception {
    ApiClient.Result pem = new ApiClient(server.baseUrl()).get(API + "/proxy/ca.pem");
    assertThat(pem.status()).as(pem.body()).isEqualTo(200);
    KeyStore trust = KeyStore.getInstance(KeyStore.getDefaultType());
    trust.load(null, null);
    trust.setCertificateEntry("mockhub", CertificateFactory.getInstance("X.509")
        .generateCertificate(new ByteArrayInputStream(pem.body().getBytes(StandardCharsets.US_ASCII))));
    TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
    tmf.init(trust);
    SSLContext ssl = SSLContext.getInstance("TLS");
    ssl.init(null, tmf.getTrustManagers(), null);
    return ssl;
  }

  @Test
  void refusesProxiedCallsUntilAProjectIsChosen() throws Exception {
    // Given
    start(Map.of("MOCKHUB_PROXY", "true"));

    // When
    HttpResponse<String> r = call(client(null), "http://partner.example.com/hello");

    // Then
    assertThat(r.statusCode()).isEqualTo(503);
    assertThat(r.body()).contains("Choose a project");
  }

  @Test
  void answersHttpAndHttpsCallsFromTheChosenProjectsStubs() throws Exception {
    // Given
    String id = start(Map.of("MOCKHUB_PROXY", "true"));
    ApiClient.Result chosen = api.put(API + "/proxy", "{\"projectId\":\"" + id + "\"}");

    // When
    HttpResponse<String> plain = call(client(null), "http://partner.example.com/hello");
    HttpResponse<String> secure = call(client(trustingTheProxyCa()), "https://partner.example.com/hello");

    // Then
    assertThat(chosen.status()).as(chosen.body()).isEqualTo(200);
    assertThat(chosen.json().at("/project/basePath").asText()).isEqualTo("/captured");
    assertThat(plain.statusCode()).isEqualTo(200);
    assertThat(plain.body()).isEqualTo("hi from the mock");
    assertThat(secure.statusCode()).isEqualTo(200);
    assertThat(secure.body()).isEqualTo("hi from the mock");
  }

  @Test
  void forwardsUnmatchedCallsToTheRealServiceAndRecordsThemInTheProject() throws Exception {
    // Given
    partner = new WireMockServer(options().dynamicPort());
    partner.start();
    partner.stubFor(get("/real").willReturn(aResponse().withStatus(200).withBody("from the real service")));
    String id = start(Map.of("MOCKHUB_PROXY", "true"));
    api.put(API + "/proxy", "{\"projectId\":\"" + id + "\"}");

    // When
    HttpResponse<String> r = call(client(null), "http://localhost:" + partner.port() + "/real");
    ApiClient.Result log = api.get(API + "/projects/" + id + "/requests");

    // Then
    assertThat(r.statusCode()).isEqualTo(200);
    assertThat(r.body()).isEqualTo("from the real service");
    assertThat(log.body()).contains("/captured/real");
  }

  @Test
  void refusesClientsOutsideTheAllowedNetworks() throws Exception {
    // Given
    String id = start(Map.of("MOCKHUB_PROXY", "true", "MOCKHUB_PROXY_ALLOW", "10.9.9.9/32"));
    api.put(API + "/proxy", "{\"projectId\":\"" + id + "\"}");

    // When
    HttpResponse<String> r = call(client(null), "http://partner.example.com/hello");

    // Then
    assertThat(r.statusCode()).isEqualTo(403);
    assertThat(r.body()).contains("MOCKHUB_PROXY_ALLOW");
  }

  @Test
  void whenTheProxyIsOffItCannotBeConfigured() {
    // Given
    String id = start(Map.of());

    // When
    ApiClient.Result chosen = api.put(API + "/proxy", "{\"projectId\":\"" + id + "\"}");
    ApiClient.Result view = api.get(API + "/proxy");

    // Then
    assertThat(chosen.status()).isEqualTo(409);
    assertThat(view.json().get("enabled").asBoolean()).isFalse();
    assertThat(api.get(API + "/proxy/ca.pem").status()).isEqualTo(404);
  }

  @Test
  void forgetsTheProjectWhenItIsDeleted() {
    // Given
    String id = start(Map.of("MOCKHUB_PROXY", "true"));
    api.put(API + "/proxy", "{\"projectId\":\"" + id + "\"}");

    // When
    api.delete(API + "/projects/" + id);

    // Then
    assertThat(api.get(API + "/proxy").json().get("project").isNull()).isTrue();
  }
}
