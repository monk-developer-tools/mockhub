package io.mockhub.system;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mockhub.tls.TlsFixtures;
import io.mockhub.tls.TrustedClients;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TlsSystemTest {

  private static final String AUTH = "Basic " + Base64.getEncoder().encodeToString("lead:s3cret".getBytes(StandardCharsets.UTF_8));
  private final HttpClient http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(10)).build();

  @TempDir
  Path data;

  @Test
  void thePackagedJarServesHttpsWithAnUploadedCertificateWhenTheHttpsPortIsSet() throws Exception {
    // Given
    int httpsPort = MockHubProcess.freePort();
    try (MockHubProcess hub = MockHubProcess.start(data, Map.of("MOCKHUB_USERS", "lead:s3cret:admin", "MOCKHUB_HTTPS_PORT", String.valueOf(httpsPort)))) {
      String body = new ObjectMapper().createObjectNode().put("pem", TlsFixtures.SERVER).toString();
      HttpResponse<String> saved = http.send(HttpRequest.newBuilder(URI.create(hub.baseUrl() + "/__admin/mockhub/api/tls/server"))
          .header("Authorization", AUTH).header("Content-Type", "application/json").PUT(HttpRequest.BodyPublishers.ofString(body)).build(),
          HttpResponse.BodyHandlers.ofString());

      // When
      HttpResponse<String> health = verifyingClient().send(HttpRequest.newBuilder(URI.create("https://localhost:" + httpsPort + "/__admin/health"))
          .timeout(Duration.ofSeconds(30)).build(), HttpResponse.BodyHandlers.ofString());

      // Then
      assertThat(saved.statusCode()).as(saved.body()).isEqualTo(200);
      assertThat(health.statusCode()).isEqualTo(200);
      assertThat(hub.stdout()).contains("HTTPS:   https://localhost:" + httpsPort);
    }
  }

  private static HttpClient verifyingClient() throws Exception {
    TrustManagerFactory trust = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
    trust.init(TrustedClients.parse(TlsFixtures.CA).trustStore());
    SSLContext context = SSLContext.getInstance("TLS");
    context.init(null, trust.getTrustManagers(), null);
    return HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).sslContext(context).connectTimeout(Duration.ofSeconds(10)).build();
  }
}
