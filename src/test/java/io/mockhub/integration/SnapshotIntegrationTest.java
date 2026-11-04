package io.mockhub.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SnapshotIntegrationTest {

  private static final String API = "/__admin/mockhub/api";

  @TempDir
  Path data;
  private MockHubServer server;
  private ApiClient admin;
  private String shopId;
  private String qaId;

  @BeforeEach
  void start() {
    server = MockHubServer.start(data, Map.of("MOCKHUB_USERS", "admin:pw:admin"));
    admin = server.api().login("admin", "pw");
    shopId = admin.post(API + "/projects", "{\"name\":\"Shop\",\"slug\":\"shop\"}").json().get("id").asText();
    qaId = admin.post(API + "/projects", "{\"name\":\"Shop QA\",\"slug\":\"shop-qa\"}").json().get("id").asText();
  }

  @AfterEach
  void stop() {
    server.close();
  }

  private String stub(String projectId, String name, String url, int status) {
    ApiClient.Result created = admin.post(API + "/projects/" + projectId + "/stubs",
        "{\"name\":\"" + name + "\",\"request\":{\"method\":\"GET\",\"urlPath\":\"" + url + "\"},\"response\":{\"status\":" + status + "}}");
    assertThat(created.status()).as(created.body()).isEqualTo(201);
    return created.json().get("id").asText();
  }

  private String file(String projectId, String name, String content) {
    return admin.send("POST", API + "/projects/" + projectId + "/files?name=" + name, content, "text/csv").json().get("path").asText();
  }

  private String snapshot(String projectId, String name) {
    ApiClient.Result created = admin.post(API + "/projects/" + projectId + "/snapshots", "{\"name\":\"" + name + "\",\"purpose\":\"before the demo\"}");
    assertThat(created.status()).as(created.body()).isEqualTo(201);
    return created.json().get("id").asText();
  }

  private int call(String path) {
    return server.api().get(path).status();
  }

  private List<String> order(String projectId) {
    List<String> ids = new ArrayList<>();
    admin.get(API + "/projects/" + projectId + "/matching-order").json().forEach(n -> ids.add(n.asText()));
    return ids;
  }

  @Test
  void restoringASnapshotBringsBackStubsFilesSettingsAndOrderAfterSavingTheCurrentState() {
    // Given
    String books = stub(shopId, "Books", "/books", 200);
    String authors = stub(shopId, "Authors", "/authors", 200);
    String path = file(shopId, "prices.csv", "isbn,price\n1,9.99\n");
    admin.post(API + "/projects/" + shopId + "/stubs",
        "{\"name\":\"Prices\",\"request\":{\"method\":\"GET\",\"urlPath\":\"/prices\"},\"response\":{\"status\":200,\"bodyFileName\":\"" + path + "\"}}");
    admin.put(API + "/projects/" + shopId + "/runtime", "{\"defaultResponse\":{\"status\":418,\"body\":\"nothing here\"}}");
    List<String> ids = new ArrayList<>(order(shopId));
    java.util.Collections.reverse(ids);
    admin.put(API + "/projects/" + shopId + "/matching-order", "{\"ids\":" + JsonList.of(ids) + "}");
    List<String> orderBefore = order(shopId);
    String v1 = snapshot(shopId, "v1");

    admin.delete(API + "/projects/" + shopId + "/stubs/" + books);
    admin.put(API + "/projects/" + shopId + "/stubs/" + authors,
        "{\"name\":\"Authors\",\"request\":{\"method\":\"GET\",\"urlPath\":\"/authors\"},\"response\":{\"status\":503}}");
    stub(shopId, "Reviews", "/reviews", 200);
    admin.delete(API + "/projects/" + shopId + "/files?name=prices.csv");
    admin.put(API + "/projects/" + shopId + "/runtime", "{}");

    // When
    JsonNode diff = admin.get(API + "/projects/" + shopId + "/snapshots/compare?from=" + v1 + "&to=current").json();
    ApiClient.Result restored = admin.post(API + "/projects/" + shopId + "/snapshots/" + v1 + "/restore", "{}");

    // Then
    assertThat(diff.at("/counts/added").asInt()).isEqualTo(1);
    assertThat(diff.at("/counts/removed").asInt()).isEqualTo(1);
    assertThat(diff.at("/counts/changed").asInt()).isEqualTo(1);
    assertThat(diff.at("/files/removed/0").asText()).isEqualTo("prices.csv");
    assertThat(diff.at("/runtime/changed").asBoolean()).isTrue();
    assertThat(restored.status()).as(restored.body()).isEqualTo(200);
    assertThat(restored.json().at("/safety/name").asText()).isEqualTo("Before restoring “v1”");
    assertThat(call("/shop/books")).isEqualTo(200);
    assertThat(call("/shop/authors")).isEqualTo(200);
    assertThat(call("/shop/reviews")).isEqualTo(418);
    assertThat(server.api().get("/shop/prices").body()).contains("9.99");
    assertThat(order(shopId)).isEqualTo(orderBefore);
    JsonNode list = admin.get(API + "/projects/" + shopId + "/snapshots").json();
    assertThat(list).hasSize(2);
    assertThat(list.get(0).get("safety").asBoolean()).isTrue();
    assertThat(list.get(1).get("purpose").asText()).isEqualTo("before the demo");
    JsonNode back = admin.get(API + "/projects/" + shopId + "/snapshots/compare?from=" + v1 + "&to=current").json();
    assertThat(back.get("identical").asBoolean()).as(back.toString()).isTrue();
  }

  @Test
  void pullingIntoAnotherProjectRewritesTheBasePathAndKeepsStubIdsStableAcrossPulls() {
    // Given
    String path = file(shopId, "prices.csv", "isbn,price\n1,9.99\n");
    admin.post(API + "/projects/" + shopId + "/stubs",
        "{\"name\":\"Prices\",\"request\":{\"method\":\"GET\",\"urlPath\":\"/prices\"},\"response\":{\"status\":200,\"bodyFileName\":\"" + path + "\"}}");
    stub(qaId, "Old QA stub", "/old", 200);

    // When
    JsonNode preview = admin.get(API + "/projects/" + qaId + "/pull/preview?from=" + shopId).json();
    ApiClient.Result pulled = admin.post(API + "/projects/" + qaId + "/pull", "{\"from\":\"" + shopId + "\"}");
    List<String> firstIds = order(qaId);
    admin.post(API + "/projects/" + qaId + "/pull", "{\"from\":\"" + shopId + "\"}");

    // Then
    assertThat(preview.at("/counts/added").asInt()).isEqualTo(1);
    assertThat(preview.at("/counts/removed").asInt()).isEqualTo(1);
    assertThat(pulled.status()).as(pulled.body()).isEqualTo(200);
    assertThat(server.api().get("/shop-qa/prices").body()).contains("9.99");
    assertThat(call("/shop-qa/old")).isEqualTo(404);
    assertThat(call("/shop/prices")).isEqualTo(200);
    assertThat(order(qaId)).isEqualTo(firstIds);
    assertThat(order(qaId)).doesNotContainAnyElementsOf(order(shopId));
    JsonNode audit = admin.get(API + "/projects/" + qaId + "/audit").json();
    assertThat(audit.toString()).contains("pulled").contains("Shop");
  }

  @Test
  void aDownloadedSnapshotUploadsIntoAnotherProjectAndBadFilesAreRefused() {
    // Given
    stub(shopId, "Books", "/books", 200);
    String v1 = snapshot(shopId, "v1");
    ApiClient.Result downloaded = admin.get(API + "/projects/" + shopId + "/snapshots/" + v1 + "/download");

    // When
    ApiClient.Result uploaded = admin.post(API + "/projects/" + qaId + "/snapshots/upload", downloaded.body());
    ApiClient.Result restored = admin.post(API + "/projects/" + qaId + "/snapshots/" + uploaded.json().get("id").asText() + "/restore", "{}");
    ApiClient.Result notASnapshot = admin.post(API + "/projects/" + qaId + "/snapshots/upload", "{\"projects\":[]}");
    ApiClient.Result badFile = admin.post(API + "/projects/" + qaId + "/snapshots/upload",
        "{\"format\":\"mockhub-snapshot\",\"stubs\":[],\"files\":{\"../secret\":\"aGk=\"}}");

    // Then
    assertThat(downloaded.contentDisposition()).contains("mockhub-snapshot-shop-v1.json");
    assertThat(uploaded.status()).as(uploaded.body()).isEqualTo(201);
    assertThat(uploaded.json().get("stubCount").asInt()).isEqualTo(1);
    assertThat(restored.status()).as(restored.body()).isEqualTo(200);
    assertThat(call("/shop-qa/books")).isEqualTo(200);
    assertThat(notASnapshot.status()).isEqualTo(400);
    assertThat(badFile.status()).isEqualTo(400);
  }

  @Test
  void viewersCompareButCannotRestoreAndTokensCannotPullFromProjectsOutsideTheirScope() {
    // Given
    stub(shopId, "Books", "/books", 200);
    String v1 = snapshot(shopId, "v1");
    ApiClient viewer = server.api().bearer(admin.post(API + "/auth/tokens", "{\"name\":\"v\",\"role\":\"viewer\"}").json().get("secret").asText());
    ApiClient qaOnly = server.api().bearer(admin.post(API + "/auth/tokens",
        "{\"name\":\"qa\",\"role\":\"editor\",\"projectIds\":[\"" + qaId + "\"]}").json().get("secret").asText());

    // When
    int compare = viewer.get(API + "/projects/" + shopId + "/snapshots/compare?from=" + v1).status();
    int restore = viewer.post(API + "/projects/" + shopId + "/snapshots/" + v1 + "/restore", "{}").status();
    int pull = qaOnly.post(API + "/projects/" + qaId + "/pull", "{\"from\":\"" + shopId + "\"}").status();

    // Then
    assertThat(compare).isEqualTo(200);
    assertThat(restore).isEqualTo(403);
    assertThat(pull).isEqualTo(404);
    assertThat(call("/shop-qa/books")).isEqualTo(404);
  }

  @Test
  void deletingAProjectDeletesItsSnapshotsAndDeletingASnapshotIsAudited() {
    // Given
    String v1 = snapshot(shopId, "v1");
    snapshot(qaId, "qa-1");

    // When
    ApiClient.Result deleted = admin.delete(API + "/projects/" + shopId + "/snapshots/" + v1);
    String v2 = snapshot(shopId, "v2");
    admin.delete(API + "/projects/" + shopId);

    // Then
    assertThat(deleted.status()).isEqualTo(200);
    assertThat(v2).isNotBlank();
    assertThat(Files.exists(data.resolve("mockhub").resolve("snapshots").resolve(shopId))).isFalse();
    assertThat(Files.exists(data.resolve("mockhub").resolve("snapshots").resolve(qaId))).isTrue();
    JsonNode audit = admin.get(API + "/audit").json();
    assertThat(audit.toString()).contains("deleted").contains("\"targetName\":\"v1\"").contains("took");
  }

  private static final class JsonList {
    static String of(List<String> ids) {
      return ids.stream().map(id -> "\"" + id + "\"").reduce((a, b) -> a + "," + b).map(s -> "[" + s + "]").orElse("[]");
    }
  }
}
