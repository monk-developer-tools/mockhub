package io.mockhub.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EnvironmentsIntegrationTest {

  private static final String API = "/__admin/mockhub/api";
  private static final String HEADER = "X-MockHub-Environment";

  @TempDir
  Path data;
  private MockHubServer server;
  private ApiClient admin;
  private String payId;

  @BeforeEach
  void start() {
    server = MockHubServer.start(data, Map.of("MOCKHUB_USERS", "admin:pw:admin,vic:pw:viewer"));
    admin = server.api().login("admin", "pw");
    payId = admin.post(API + "/projects", "{\"name\":\"Payments\",\"slug\":\"pay\"}").json().get("id").asText();
    admin.post(API + "/projects/" + payId + "/stubs", "{\"name\":\"Decline in qa\",\"priority\":1,"
        + "\"request\":{\"method\":\"POST\",\"urlPath\":\"/charges\",\"headers\":{\"" + HEADER + "\":{\"matches\":\"qa\"}}},"
        + "\"response\":{\"status\":402,\"body\":\"declined\"}}");
    admin.post(API + "/projects/" + payId + "/stubs", "{\"name\":\"Approve\",\"priority\":5,\"request\":{\"method\":\"POST\",\"urlPath\":\"/charges\"},"
        + "\"response\":{\"status\":201,\"body\":\"{{env}} {{env 'bank'}} {{env 'missing'}}.\",\"transformers\":[\"response-template\"]}}");
  }

  @AfterEach
  void stop() {
    server.close();
  }

  private ApiClient.Result charge(String environment) {
    return server.api().send("POST", "/pay/charges", "{}", "application/json", environment == null ? Map.of() : Map.of(HEADER, environment));
  }

  @Test
  void aProjectWithoutEnvironmentsLeavesRequestsAlone() {
    // When
    ApiClient.Result plain = charge(null);
    ApiClient.Result asked = charge("qa");

    // Then
    assertThat(plain.status()).isEqualTo(201);
    assertThat(plain.body()).isEqualTo("  .");
    assertThat(asked.status()).isEqualTo(402);
  }

  @Test
  void stubsAndTemplatesFollowTheActiveEnvironmentAndTheHeader() {
    // Given
    ApiClient.Result saved = admin.put(API + "/projects/" + payId + "/environments", "{\"active\":\"dev\",\"environments\":["
        + "{\"name\":\"dev\",\"variables\":{\"bank\":\"sandbox-bank\"}},{\"name\":\"qa\",\"variables\":{\"bank\":\"qa-bank\"}}]}");

    // When
    ApiClient.Result dev = charge(null);
    ApiClient.Result qa = charge("qa");
    ApiClient.Result unknown = charge("prod");
    admin.put(API + "/projects/" + payId + "/environments/active", "{\"name\":\"qa\"}");
    ApiClient.Result switched = charge(null);
    ApiClient.Result back = charge("dev");

    // Then
    assertThat(saved.status()).as(saved.body()).isEqualTo(200);
    assertThat(dev.status()).as(dev.body()).isEqualTo(201);
    assertThat(dev.body()).isEqualTo("dev sandbox-bank .");
    assertThat(qa.status()).as(qa.body()).isEqualTo(402);
    assertThat(unknown.body()).isEqualTo("dev sandbox-bank .");
    assertThat(switched.status()).isEqualTo(402);
    assertThat(back.body()).isEqualTo("dev sandbox-bank .");
    assertThat(admin.get(API + "/projects/" + payId + "/environments").json().get("active").asText()).isEqualTo("qa");
  }

  @Test
  void viewersSeeEnvironmentsAndOnlyEditorsChangeThem() {
    // Given
    admin.put(API + "/projects/" + payId + "/environments", "{\"environments\":[{\"name\":\"dev\"},{\"name\":\"qa\"}]}");
    ApiClient viewer = server.api().login("vic", "pw");

    // When
    ApiClient.Result read = viewer.get(API + "/projects/" + payId + "/environments");
    ApiClient.Result change = viewer.put(API + "/projects/" + payId + "/environments/active", "{\"name\":\"qa\"}");
    ApiClient.Result bad = admin.put(API + "/projects/" + payId + "/environments/active", "{\"name\":\"prod\"}");

    // Then
    assertThat(read.status()).isEqualTo(200);
    assertThat(read.json().get("environments")).hasSize(2);
    assertThat(change.status()).isEqualTo(403);
    assertThat(bad.status()).isEqualTo(404);
  }

  @Test
  void anAgentSetsUpEnvironmentsOverMcp() {
    // Given
    String secret = admin.post(API + "/auth/tokens", "{\"name\":\"Agent\",\"role\":\"editor\"}").json().get("secret").asText();
    String set = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\",\"params\":{\"name\":\"set_environments\",\"arguments\":"
        + "{\"project\":\"pay\",\"active\":\"qa\",\"environments\":[{\"name\":\"dev\",\"variables\":{\"bank\":\"sandbox\"}},{\"name\":\"qa\",\"variables\":{\"bank\":\"qa-bank\"}}]}}}";
    String change = "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/call\",\"params\":{\"name\":\"switch_environment\",\"arguments\":{\"project\":\"pay\",\"name\":\"dev\"}}}";

    // When
    ApiClient.Result first = server.api().bearer(secret).post("/__admin/mockhub/mcp", set);
    ApiClient.Result qa = charge(null);
    server.api().bearer(secret).post("/__admin/mockhub/mcp", change);
    ApiClient.Result dev = charge(null);

    // Then
    assertThat(first.json().at("/result/isError").asBoolean()).as(first.body()).isFalse();
    assertThat(qa.status()).isEqualTo(402);
    assertThat(dev.body()).isEqualTo("dev sandbox .");
  }

  private static final String DEV_QA = "{\"active\":\"dev\",\"environments\":[{\"name\":\"dev\",\"variables\":{\"bank\":\"sandbox-bank\"}},"
      + "{\"name\":\"qa\",\"variables\":{\"bank\":\"qa-bank\"}}]}";

  @Test
  void snapshotsBringVariablesBackAndLeaveTheActiveSwitchAlone() {
    // Given
    admin.put(API + "/projects/" + payId + "/environments", DEV_QA);
    String snapshot = admin.post(API + "/projects/" + payId + "/snapshots", "{\"name\":\"before\"}").json().get("id").asText();
    admin.put(API + "/projects/" + payId + "/environments", "{\"active\":\"qa\",\"environments\":[{\"name\":\"dev\",\"variables\":{\"bank\":\"changed\"}},{\"name\":\"qa\"}]}");

    // When
    ApiClient.Result diff = admin.get(API + "/projects/" + payId + "/snapshots/compare?from=current&to=" + snapshot);
    ApiClient.Result restored = admin.post(API + "/projects/" + payId + "/snapshots/" + snapshot + "/restore", "{}");
    ApiClient.Result after = admin.get(API + "/projects/" + payId + "/environments");

    // Then
    assertThat(diff.json().at("/environments/changed").asBoolean()).as(diff.body()).isTrue();
    assertThat(diff.json().get("identical").asBoolean()).isFalse();
    assertThat(restored.status()).as(restored.body()).isEqualTo(200);
    assertThat(after.json().at("/environments/0/variables/bank").asText()).isEqualTo("sandbox-bank");
    assertThat(after.json().get("active").asText()).isEqualTo("qa");
    assertThat(charge("dev").body()).isEqualTo("dev sandbox-bank .");
  }

  @Test
  void backupsRestoreEnvironmentsWithTheActiveOne() {
    // Given
    admin.put(API + "/projects/" + payId + "/environments", DEV_QA);
    admin.put(API + "/projects/" + payId + "/environments/active", "{\"name\":\"qa\"}");
    String backup = admin.get(API + "/backup").body();
    admin.put(API + "/projects/" + payId + "/environments", "{\"environments\":[]}");

    // When
    ApiClient.Result restored = admin.post(API + "/restore", backup);
    ApiClient.Result after = admin.get(API + "/projects/" + payId + "/environments");

    // Then
    assertThat(restored.status()).as(restored.body()).isEqualTo(200);
    assertThat(after.json().get("environments")).hasSize(2);
    assertThat(after.json().get("active").asText()).isEqualTo("qa");
  }
}
