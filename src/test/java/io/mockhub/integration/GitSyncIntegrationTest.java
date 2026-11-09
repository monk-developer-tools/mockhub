package io.mockhub.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import io.mockhub.api.Jsons;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.transport.RefSpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GitSyncIntegrationTest {

  private static final String API = "/__admin/mockhub/api";

  @TempDir
  Path data;
  @TempDir
  Path repos;
  private MockHubServer server;
  private ApiClient admin;
  private String shopId;
  private String remote;

  @BeforeEach
  void start() throws Exception {
    Path bare = repos.resolve("mocks.git");
    Git.init().setBare(true).setDirectory(bare.toFile()).setInitialBranch("main").call().close();
    remote = bare.toUri().toString();
    server = MockHubServer.start(data, Map.of("MOCKHUB_USERS", "admin:pw:admin,ann:pw:editor", "MOCKHUB_GIT_ALLOW_PRIVATE", "true"));
    admin = server.api().login("admin", "pw");
    shopId = admin.post(API + "/projects", "{\"name\":\"Shop\",\"slug\":\"shop\"}").json().get("id").asText();
    admin.post(API + "/projects/" + shopId + "/stubs",
        "{\"name\":\"List items\",\"request\":{\"method\":\"GET\",\"urlPath\":\"/items\"},\"response\":{\"status\":200,\"jsonBody\":[{\"id\":1}]}}");
  }

  @AfterEach
  void stop() {
    server.close();
  }

  private Path checkout() throws Exception {
    Path dir = Files.createTempDirectory(repos, "work");
    Git.cloneRepository().setURI(remote).setDirectory(dir.toFile()).setBranch("main").call().close();
    return dir;
  }

  @Test
  void pushesAProjectAsFilesAndPullsAChangeMadeInTheRepository() throws Exception {
    // Given
    JsonNode linked = admin.put(API + "/projects/" + shopId + "/git", "{\"url\":\"" + remote + "\",\"token\":\"ghp_secret\"}").json();
    JsonNode pushed = admin.post(API + "/projects/" + shopId + "/git/push", "{\"message\":\"Add items\"}").json();
    Path work = checkout();
    Path stubFile;
    try (var files = Files.list(work.resolve("mockhub/shop/mappings"))) {
      stubFile = files.findFirst().orElseThrow();
    }
    Files.writeString(stubFile, Files.readString(stubFile).replace("\"id\" : 1", "\"id\" : 2"));
    try (Git git = Git.open(work.toFile())) {
      git.commit().setAll(true).setMessage("Answer with item 2").setAuthor(new PersonIdent("Dev", "dev@example.com")).setSign(false).call();
      git.push().call();
    }

    // When
    JsonNode preview = admin.get(API + "/projects/" + shopId + "/git/preview").json();
    JsonNode pulled = admin.post(API + "/projects/" + shopId + "/git/pull", "{}").json();
    JsonNode again = admin.post(API + "/projects/" + shopId + "/git/push", "{}").json();
    JsonNode view = admin.get(API + "/projects/" + shopId + "/git").json();

    // Then
    assertThat(linked.get("folder").asText()).isEqualTo("mockhub/shop");
    assertThat(linked.has("token")).isFalse();
    assertThat(linked.get("tokenSet").asBoolean()).isTrue();
    assertThat(pushed.get("changed").asBoolean()).as(pushed.toString()).isTrue();
    assertThat(stubFile.getFileName().toString()).startsWith("list-items-");
    assertThat(Files.readString(work.resolve("mockhub/shop/mockhub.json"))).contains("\"format\" : \"mockhub-git\"");
    try (Git git = Git.open(work.toFile())) {
      assertThat(git.log().call().iterator().next().getFullMessage()).isEqualTo("Answer with item 2");
    }
    assertThat(preview.toString()).as(preview.toString()).contains("changed");
    assertThat(pulled.get("stubs").asInt()).isEqualTo(1);
    assertThat(pulled.at("/safety/safety").asBoolean()).isTrue();
    assertThat(server.api().get("/shop/items").body()).contains("2");
    assertThat(again.get("changed").asBoolean()).isFalse();
    assertThat(view.at("/lastPush/by").asText()).isEqualTo("admin");
    assertThat(view.at("/lastPull/commit").asText()).isEqualTo(pulled.get("commit").asText());
    assertThat(Files.readString(data.resolve("mockhub/git-links.json"))).doesNotContain("ghp_secret");
  }

  @Test
  void onlyManagersLinkAndEditorsPushAndPull() {
    // Given
    admin.put(API + "/projects/" + shopId + "/git", "{\"url\":\"" + remote + "\"}");
    ApiClient ann = server.api().login("ann", "pw");

    // When
    int link = ann.put(API + "/projects/" + shopId + "/git", "{\"url\":\"" + remote + "\"}").status();
    int unlink = ann.delete(API + "/projects/" + shopId + "/git").status();
    int push = ann.post(API + "/projects/" + shopId + "/git/push", "{}").status();
    int read = ann.get(API + "/projects/" + shopId + "/git").status();

    // Then
    assertThat(link).isEqualTo(403);
    assertThat(unlink).isEqualTo(403);
    assertThat(push).isEqualTo(200);
    assertThat(read).isEqualTo(200);
  }

  @Test
  void explainsAWrongLinkBeforeTouchingTheProject() {
    // When
    ApiClient.Result ssh = admin.put(API + "/projects/" + shopId + "/git", "{\"url\":\"git@github.com:acme/mocks.git\"}");
    ApiClient.Result tokenInUrl = admin.put(API + "/projects/" + shopId + "/git", "{\"url\":\"https://me:ghp_x@github.com/acme/mocks.git\"}");
    ApiClient.Result badFolder = admin.put(API + "/projects/" + shopId + "/git", "{\"url\":\"" + remote + "\",\"folder\":\"../up\"}");
    admin.put(API + "/projects/" + shopId + "/git", "{\"url\":\"" + remote + "\",\"branch\":\"mocks\"}");
    ApiClient.Result noBranch = admin.get(API + "/projects/" + shopId + "/git/preview");
    int before = server.api().get("/shop/items").status();

    // Then
    assertThat(ssh.status()).isEqualTo(400);
    assertThat(ssh.body()).contains("https://");
    assertThat(tokenInUrl.body()).contains("token field");
    assertThat(badFolder.body()).contains("inside the repository");
    assertThat(noBranch.status()).isEqualTo(404);
    assertThat(noBranch.body()).contains("Push first");
    assertThat(before).isEqualTo(200);
  }

  @Test
  void pullsAPlainWireMockRepository() throws Exception {
    // Given
    Path work = checkout();
    Files.createDirectories(work.resolve("mappings"));
    Files.writeString(work.resolve("mappings/health.json"),
        "{\"request\":{\"method\":\"GET\",\"url\":\"/health\"},\"response\":{\"status\":200,\"body\":\"UP\"}}", StandardCharsets.UTF_8);
    try (Git git = Git.open(work.toFile())) {
      git.add().addFilepattern(".").call();
      git.commit().setMessage("WireMock mappings").setAuthor(new PersonIdent("Dev", "dev@example.com")).setSign(false).call();
      git.push().setRefSpecs(new RefSpec("HEAD:refs/heads/main")).call();
    }
    admin.put(API + "/projects/" + shopId + "/git", "{\"url\":\"" + remote + "\",\"folder\":\"\"}");

    // When
    JsonNode pulled = admin.post(API + "/projects/" + shopId + "/git/pull", "{}").json();

    // Then
    assertThat(pulled.path("stubs").asInt()).as(Jsons.write(pulled)).isEqualTo(1);
    assertThat(server.api().get("/shop/health").body()).isEqualTo("UP");
    assertThat(server.api().get("/shop/items").status()).isEqualTo(404);
  }

  @Test
  void anAgentPushesOverMcp() throws Exception {
    // Given
    admin.put(API + "/projects/" + shopId + "/git", "{\"url\":\"" + remote + "\"}");
    String secret = admin.post(API + "/auth/tokens", "{\"name\":\"Claude Code\",\"role\":\"editor\"}").json().get("secret").asText();
    String call = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\",\"params\":{\"name\":\"git_push\",\"arguments\":{\"project\":\"shop\",\"message\":\"From the agent\"}}}";

    // When
    JsonNode result = server.api().bearer(secret).post("/__admin/mockhub/mcp", call).json().path("result");

    // Then
    assertThat(result.path("isError").asBoolean()).as(result.toString()).isFalse();
    try (Git git = Git.open(checkout().toFile())) {
      assertThat(git.log().call().iterator().next().getFullMessage()).isEqualTo("From the agent");
      assertThat(git.log().call().iterator().next().getAuthorIdent().getName()).contains("Claude Code");
    }
  }

  @Test
  void environmentsTravelThroughTheRepositoryWithoutTheActiveSwitch() throws Exception {
    // Given
    admin.put(API + "/projects/" + shopId + "/git", "{\"url\":\"" + remote + "\"}");
    admin.put(API + "/projects/" + shopId + "/environments", "{\"active\":\"dev\",\"environments\":[{\"name\":\"dev\",\"variables\":{\"bank\":\"sandbox\"}},{\"name\":\"qa\"}]}");
    admin.post(API + "/projects/" + shopId + "/git/push", "{}");
    String manifest = Files.readString(checkout().resolve("mockhub/shop/mockhub.json"));

    // When
    admin.put(API + "/projects/" + shopId + "/environments/active", "{\"name\":\"qa\"}");
    JsonNode afterSwitch = admin.post(API + "/projects/" + shopId + "/git/push", "{}").json();
    admin.put(API + "/projects/" + shopId + "/environments", "{\"active\":\"qa\",\"environments\":[{\"name\":\"qa\"}]}");
    JsonNode preview = admin.get(API + "/projects/" + shopId + "/git/preview").json();
    admin.post(API + "/projects/" + shopId + "/git/pull", "{}");
    JsonNode pulled = admin.get(API + "/projects/" + shopId + "/environments").json();

    // Then
    assertThat(manifest).contains("sandbox");
    assertThat(afterSwitch.get("changed").asBoolean()).isFalse();
    assertThat(preview.at("/environments/changed").asBoolean()).as(preview.toString()).isTrue();
    assertThat(pulled.get("environments")).hasSize(2);
    assertThat(pulled.get("active").asText()).isEqualTo("qa");
  }
}
