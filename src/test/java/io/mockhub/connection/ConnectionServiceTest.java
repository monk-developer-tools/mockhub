package io.mockhub.connection;

import static io.mockhub.support.Fixtures.json;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.mockhub.org.Organization;
import io.mockhub.playground.Header;
import io.mockhub.playground.HttpSender;
import io.mockhub.support.MutableClock;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConnectionServiceTest {

  private record Sent(URI uri, List<Header> headers, String body) {}

  private final List<Sent> sent = new ArrayList<>();
  private final MutableClock clock = new MutableClock(Instant.parse("2026-11-04T10:00:00Z"));
  private int status = 200;
  private String answer = "{\"access_token\":\"tok-1\",\"token_type\":\"Bearer\",\"expires_in\":120,\"scope\":\"payments\"}";
  private final HttpSender sender = (uri, method, headers, body, timeout, max) -> {
    sent.add(new Sent(uri, headers, body));
    return new HttpSender.Raw(status, List.of(), answer.getBytes(StandardCharsets.UTF_8), false);
  };

  @TempDir
  Path dir;
  private ConnectionService service;

  @BeforeEach
  void start() {
    service = new ConnectionService(new FileConnectionRepository(dir), SecretBox.of("test key", dir.resolve("k")),
        new TokenClient(sender, URI::create), clock);
  }

  private void bank() {
    service.create("org1", json("{'key':'bank','name':'Bank API','tokenUrl':'https://login.bank.example/token','clientId':'app 1',"
        + "'secret':'p@ss','scope':'payments','audience':'https://api.bank.example'}"));
  }

  private static String header(Sent s, String name) {
    return s.headers().stream().filter(h -> h.name().equals(name)).map(Header::value).findFirst().orElse(null);
  }

  @Test
  void fetchesATokenWithBasicAuthAndKeepsItUntilItIsAboutToExpire() {
    // Given
    bank();

    // When
    Header first = service.header("org1", "bank");
    clock.advance(Duration.ofSeconds(80));
    Header cached = service.header("org1", "bank");
    clock.advance(Duration.ofSeconds(15));
    answer = answer.replace("tok-1", "tok-2");
    Header refreshed = service.header("org1", "bank");

    // Then
    assertThat(first).isEqualTo(new Header("Authorization", "Bearer tok-1"));
    assertThat(cached.value()).isEqualTo("Bearer tok-1");
    assertThat(refreshed.value()).isEqualTo("Bearer tok-2");
    assertThat(sent).hasSize(2);
    assertThat(header(sent.get(0), "Authorization")).isEqualTo("Basic YXBwKzE6cCU0MHNz");
    assertThat(sent.get(0).body()).isEqualTo("grant_type=client_credentials&scope=payments&audience=https%3A%2F%2Fapi.bank.example");
  }

  @Test
  void canSendTheClientCredentialsInTheBody() {
    // Given
    service.create("org1", json("{'key':'idp','tokenUrl':'https://idp.example/token','clientId':'app','secret':'s','clientAuth':'body'}"));

    // When
    service.header("org1", "idp");

    // Then
    assertThat(header(sent.get(0), "Authorization")).isNull();
    assertThat(sent.get(0).body()).isEqualTo("grant_type=client_credentials&client_id=app&client_secret=s");
  }

  @Test
  void keepsSecretsEncryptedAndOutOfViewsAndBackups() throws Exception {
    // Given
    bank();

    // When
    String onDisk = Files.readString(dir.resolve("mockhub/connections/org1.json"));

    // Then
    assertThat(onDisk).doesNotContain("p@ss").contains("v1:");
    assertThat(ConnectionService.view(service.get("org1", "bank")).toString()).doesNotContain("v1:").contains("\"hasSecret\":true");
    assertThat(service.snapshot("org1").get(0).hasSecret()).isFalse();
  }

  @Test
  void changingSettingsKeepsTheSecretUnlessANewOneIsGivenAndDropsTheCachedToken() {
    // Given
    bank();
    service.header("org1", "bank");

    // When
    service.update("org1", "bank", json("{'scope':'accounts'}"));
    service.header("org1", "bank");
    service.update("org1", "bank", json("{'secret':'new'}"));
    service.header("org1", "bank");

    // Then
    assertThat(sent).hasSize(3);
    assertThat(sent.get(1).body()).contains("scope=accounts");
    assertThat(header(sent.get(1), "Authorization")).isEqualTo(header(sent.get(0), "Authorization"));
    assertThat(header(sent.get(2), "Authorization")).isEqualTo("Basic YXBwKzE6bmV3");
  }

  @Test
  void testsAConnectionAndShowsOnlyAMaskedToken() {
    // Given
    bank();
    answer = "{\"access_token\":\"eyJhbGciOiJSUzI1NiJ9.payload.signature\",\"expires_in\":3600}";

    // When
    ConnectionService.TestResult result = service.test("org1", "bank");

    // Then
    assertThat(result.preview()).isEqualTo("Bearer eyJh…ture");
    assertThat(result.expiresInSeconds()).isEqualTo(3600);
    assertThat(result.tokenType()).isEqualTo("Bearer");
  }

  @Test
  void explainsTokenFailures() {
    // Given
    bank();

    // Then
    status = 401;
    answer = "{\"error\":\"invalid_client\"}";
    assertThatThrownBy(() -> service.header("org1", "bank")).isInstanceOf(ConnectionException.class)
        .hasMessage("The token endpoint answered 401: {\"error\":\"invalid_client\"}");
    status = 200;
    answer = "{\"token\":\"x\"}";
    assertThatThrownBy(() -> service.test("org1", "bank")).hasMessageContaining("has no access_token");
    assertThatThrownBy(() -> service.header("org1", "missing")).hasMessage("There is no connection called missing in this organisation");
  }

  @Test
  void aHeaderConnectionSendsItsSecretAsIs() {
    // Given
    service.create("org1", json("{'key':'partner','type':'header','headerName':'X-Api-Key','secret':'k-123'}"));

    // Then
    assertThat(service.header("org1", "partner")).isEqualTo(new Header("X-Api-Key", "k-123"));
    assertThat(sent).isEmpty();
  }

  @Test
  void checksSettings() {
    // Then
    assertThatThrownBy(() -> service.create("org1", json("{'key':'Bad'}"))).hasMessageContaining("lowercase");
    assertThatThrownBy(() -> service.create("org1", json("{'key':'a','clientId':'x','secret':'s'}"))).hasMessageContaining("token URL");
    assertThatThrownBy(() -> service.create("org1", json("{'key':'a','tokenUrl':'ftp://x/y','clientId':'x','secret':'s'}"))).hasMessageContaining("https://");
    assertThatThrownBy(() -> service.create("org1", json("{'key':'a','tokenUrl':'https://x/t','secret':'s'}"))).hasMessage("Enter the client id");
    assertThatThrownBy(() -> service.create("org1", json("{'key':'a','tokenUrl':'https://x/t','clientId':'x'}"))).hasMessage("Enter the client secret");
    assertThatThrownBy(() -> service.create("org1", json("{'key':'a','type':'header','headerName':'Bad Name','secret':'s'}"))).hasMessageContaining("header name");
    assertThatThrownBy(() -> service.create("org1", json("{'key':'a','type':'soap'}"))).hasMessage("type must be oauth2 or header");
  }

  @Test
  void restoresConnectionsWithoutSecretsAndDeletesThemWithTheOrganisation() {
    // Given
    bank();
    List<Connection> backup = service.snapshot("org1");

    // When
    service.restore("org2", backup, true);
    service.onOrganizationDeleted(new Organization("org1", "One", "one", "", "", ""));

    // Then
    assertThat(service.list("org1")).isEmpty();
    assertThat(service.get("org2", "bank").hasSecret()).isFalse();
    assertThatThrownBy(() -> service.header("org2", "bank")).hasMessageContaining("has no secret yet");
  }
}
