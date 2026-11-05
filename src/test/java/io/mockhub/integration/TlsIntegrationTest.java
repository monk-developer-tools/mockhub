package io.mockhub.integration;

import static org.assertj.core.api.Assertions.assertThat;

import io.mockhub.api.Jsons;
import io.mockhub.tls.ServerIdentity;
import io.mockhub.tls.TlsFixtures;
import io.mockhub.tls.TrustedClients;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.Map;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TlsIntegrationTest {

  private static final String API = "/__admin/mockhub/api";

  @TempDir
  Path data;
  private MockHubServer server;

  @AfterEach
  void stop() {
    if (server != null) server.close();
  }

  private ApiClient.Result put(String path, String pem) {
    return server.api().put(API + path, Jsons.object().put("pem", pem).toString());
  }

  private String projectRequiringClientCerts() {
    ApiClient api = server.api();
    String id = api.post(API + "/projects", "{\"name\":\"Payments\",\"slug\":\"payments\"}").json().get("id").asText();
    api.put(API + "/projects/" + id + "/runtime", "{\"requireClientCert\":true}");
    api.post(API + "/projects/" + id + "/stubs", "{\"request\":{\"method\":\"GET\",\"urlPath\":\"/whoami\"},"
        + "\"response\":{\"status\":200,\"body\":\"{{request.headers.X-MockHub-Client-Cert-Subject}}\",\"transformers\":[\"response-template\"]}}");
    return id;
  }

  private HttpResponse<String> callHttps(String path, String clientPem) throws Exception {
    return client(clientPem).send(HttpRequest.newBuilder(URI.create("https://localhost:" + server.httpsPort() + path)).timeout(Duration.ofSeconds(30)).build(),
        HttpResponse.BodyHandlers.ofString());
  }

  private static HttpClient client(String clientPem) throws Exception {
    TrustManagerFactory trust = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
    trust.init(TrustedClients.parse(TlsFixtures.CA).trustStore());
    KeyManagerFactory keys = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
    char[] password = "pw".toCharArray();
    keys.init(clientPem == null ? null : ServerIdentity.parse(clientPem).keyStore(password), password);
    SSLContext context = SSLContext.getInstance("TLS");
    context.init(clientPem == null ? null : keys.getKeyManagers(), trust.getTrustManagers(), null);
    return HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).sslContext(context).connectTimeout(Duration.ofSeconds(10)).build();
  }

  @Test
  void servesTheUploadedCertificateOnTheHttpsPort() throws Exception {
    // Given
    server = MockHubServer.startWithHttps(data);

    // When
    ApiClient.Result saved = put("/tls/server", TlsFixtures.SERVER);
    HttpResponse<String> response = callHttps("/__admin/health", null);

    // Then
    assertThat(saved.status()).as(saved.body()).isEqualTo(200);
    assertThat(saved.json().at("/server/subject").asText()).isEqualTo("CN=localhost");
    assertThat(response.statusCode()).isEqualTo(200);
    X509Certificate served = (X509Certificate) response.sslSession().orElseThrow().getPeerCertificates()[0];
    assertThat(served.getIssuerX500Principal().getName()).isEqualTo("CN=MockHub Test CA");
  }

  @Test
  void letsOnlyCallersWithATrustedClientCertificateIntoAProjectThatRequiresOne() throws Exception {
    // Given
    server = MockHubServer.startWithHttps(data);
    put("/tls/server", TlsFixtures.SERVER);
    put("/tls/client-authorities", TlsFixtures.CA);
    projectRequiringClientCerts();

    // When
    HttpResponse<String> withCertificate = callHttps("/payments/whoami", TlsFixtures.CLIENT);
    HttpResponse<String> withoutCertificate = callHttps("/payments/whoami", null);
    ApiClient.Result spoofedOverHttp = server.api().send("GET", "/payments/whoami", null, null,
        Map.of("X-MockHub-Client-Cert-Subject", "O=Acme,CN=billing-service"));

    // Then
    assertThat(withCertificate.statusCode()).as(withCertificate.body()).isEqualTo(200);
    assertThat(withCertificate.body()).isEqualTo("O=Acme,CN=billing-service");
    assertThat(withoutCertificate.statusCode()).isEqualTo(403);
    assertThat(withoutCertificate.body()).contains("needs a client certificate");
    assertThat(spoofedOverHttp.status()).isEqualTo(403);
  }

  @Test
  void treatsACertificateFromAnUntrustedCaAsNoCertificate() throws Exception {
    // Given
    server = MockHubServer.startWithHttps(data);
    put("/tls/server", TlsFixtures.SERVER);
    put("/tls/client-authorities", TlsFixtures.CA);
    projectRequiringClientCerts();

    // When
    HttpResponse<String> response = callHttps("/payments/whoami", TlsFixtures.ROGUE);

    // Then
    assertThat(response.statusCode()).isEqualTo(403);
    assertThat(response.body()).doesNotContain("rogue");
  }

  @Test
  void appliesSavedCertificatesAgainAfterARestart() throws Exception {
    // Given
    server = MockHubServer.startWithHttps(data);
    put("/tls/server", TlsFixtures.SERVER);
    put("/tls/client-authorities", TlsFixtures.CA);
    projectRequiringClientCerts();
    server.close();

    // When
    server = MockHubServer.startWithHttps(data);
    HttpResponse<String> response = callHttps("/payments/whoami", TlsFixtures.CLIENT);

    // Then
    assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
    assertThat(response.body()).isEqualTo("O=Acme,CN=billing-service");
  }

  @Test
  void explainsThatCertificatesNeedTheHttpsPort() {
    // Given
    server = MockHubServer.start(data);

    // When
    ApiClient.Result saved = put("/tls/server", TlsFixtures.SERVER);
    ApiClient.Result view = server.api().get(API + "/tls");

    // Then
    assertThat(saved.status()).isEqualTo(409);
    assertThat(saved.json().get("error").asText()).contains("MOCKHUB_HTTPS_PORT");
    assertThat(view.json().get("httpsEnabled").asBoolean()).isFalse();
  }

  @Test
  void rejectsAKeyThatDoesNotMatchTheCertificate() {
    // Given
    server = MockHubServer.startWithHttps(data);

    // When
    ApiClient.Result saved = put("/tls/server", TlsFixtures.certificatesOnly(TlsFixtures.SERVER) + TlsFixtures.keyOnly(TlsFixtures.CLIENT));

    // Then
    assertThat(saved.status()).isEqualTo(400);
    assertThat(saved.json().get("error").asText()).contains("doesn't belong");
  }
}
