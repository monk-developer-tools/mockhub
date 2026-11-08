package io.mockhub.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.mockhub.api.Jsons;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class McpIntegrationTest {

  private static final String API = "/__admin/mockhub/api";
  private static final String MCP = "/__admin/mockhub/mcp";

  @TempDir
  Path data;
  private MockHubServer server;
  private ApiClient admin;
  private String shopId;
  private String payId;
  private int nextId = 1;

  @BeforeEach
  void start() {
    server = MockHubServer.start(data, Map.of("MOCKHUB_USERS", "admin:pw:admin"));
    admin = server.api().login("admin", "pw");
    shopId = admin.post(API + "/projects", "{\"name\":\"Shop\",\"slug\":\"shop\"}").json().get("id").asText();
    payId = admin.post(API + "/projects", "{\"name\":\"Payments\",\"slug\":\"pay\"}").json().get("id").asText();
  }

  @AfterEach
  void stop() {
    server.close();
  }

  private String token(String body) {
    ApiClient.Result created = admin.post(API + "/auth/tokens", body);
    assertThat(created.status()).as(created.body()).isEqualTo(200);
    return created.json().get("secret").asText();
  }

  private JsonNode rpc(String token, String method, ObjectNode params) {
    ObjectNode message = Jsons.object().put("jsonrpc", "2.0").put("id", nextId++).put("method", method);
    if (params != null) message.set("params", params);
    ApiClient.Result result = server.api().bearer(token).post(MCP, Jsons.write(message));
    assertThat(result.status()).as(result.body()).isEqualTo(200);
    return result.json();
  }

  private JsonNode tool(String token, String name, String arguments) {
    ObjectNode params = Jsons.object().put("name", name);
    try {
      params.set("arguments", Jsons.MAPPER.readTree(arguments));
    } catch (Exception e) {
      throw new IllegalArgumentException(e);
    }
    return rpc(token, "tools/call", params).path("result");
  }

  private static JsonNode content(JsonNode toolResult) throws Exception {
    return Jsons.MAPPER.readTree(toolResult.at("/content/0/text").asText());
  }

  @Test
  void personalTokensWorkOnTheApiUntilRevoked() {
    // Given
    ApiClient.Result created = admin.post(API + "/auth/tokens", "{\"name\":\"CI\",\"role\":\"viewer\",\"expiresInDays\":30}");
    String secret = created.json().get("secret").asText();
    ApiClient ci = server.api().bearer(secret);

    // When
    int read = ci.get(API + "/projects").status();
    int write = ci.post(API + "/projects", "{\"name\":\"X\"}").status();
    int tokenAdmin = ci.get(API + "/auth/tokens").status();
    JsonNode listed = admin.get(API + "/auth/tokens").json();
    admin.delete(API + "/auth/tokens/" + created.json().get("id").asText());

    // Then
    assertThat(read).isEqualTo(200);
    assertThat(write).isEqualTo(403);
    assertThat(tokenAdmin).isEqualTo(403);
    assertThat(listed).singleElement().satisfies(t -> {
      assertThat(t.get("name").asText()).isEqualTo("CI");
      assertThat(t.has("secret")).isFalse();
      assertThat(t.has("hash")).isFalse();
      assertThat(t.get("lastUsedAt").isNull()).isFalse();
    });
    assertThat(ci.get(API + "/projects").status()).isEqualTo(401);
  }

  @Test
  void anAgentBuildsAndChecksAMockOverMcp() throws Exception {
    // Given
    String secret = token("{\"name\":\"Claude Code\",\"role\":\"editor\"}");

    // When
    JsonNode init = rpc(secret, "initialize", Jsons.object().put("protocolVersion", "2025-06-18"));
    JsonNode tools = rpc(secret, "tools/list", null);
    JsonNode created = tool(secret, "create_stub", "{\"project\":\"shop\",\"stub\":{\"name\":\"List items\",\"request\":{\"method\":\"GET\",\"urlPath\":\"/items\"},\"response\":{\"status\":200,\"jsonBody\":[{\"id\":1}]}}}");
    int mockStatus = server.api().get("/shop/items").status();
    server.api().get("/shop/missing");
    JsonNode stubs = tool(secret, "list_stubs", "{\"project\":\"Shop\"}");
    JsonNode unmatched = waitForUnmatched(secret);
    JsonNode audit = admin.get(API + "/projects/" + shopId + "/audit").json();

    // Then
    assertThat(init.at("/result/protocolVersion").asText()).isEqualTo("2025-06-18");
    assertThat(init.at("/result/serverInfo/name").asText()).isEqualTo("mockhub");
    List<String> names = new ArrayList<>();
    tools.at("/result/tools").forEach(t -> names.add(t.get("name").asText()));
    assertThat(names).contains("list_projects", "create_stub", "search_requests", "set_runtime", "create_crud_resource", "get_stub_reference");
    assertThat(created.path("isError").asBoolean()).as(created.toString()).isFalse();
    assertThat(mockStatus).isEqualTo(200);
    assertThat(content(stubs)).singleElement().satisfies(s -> assertThat(s.get("url").asText()).isEqualTo("/items"));
    assertThat(unmatched.at("/requests/0/url").asText()).isEqualTo("/shop/missing");
    assertThat(audit.toString()).contains("via token “Claude Code”");
  }

  private JsonNode waitForUnmatched(String secret) throws Exception {
    for (int i = 0; i < 100; i++) {
      JsonNode found = content(tool(secret, "search_requests", "{\"project\":\"shop\",\"unmatched_only\":true}"));
      if (found.path("total").asInt() > 0) return found;
      Thread.sleep(50);
    }
    throw new AssertionError("No unmatched request recorded");
  }

  @Test
  void aReviewTokenStagesChangesForSomeoneToApply() throws Exception {
    // Given
    String secret = token("{\"name\":\"Cursor\",\"role\":\"editor\",\"mode\":\"review\"}");

    // When
    JsonNode staged = tool(secret, "create_stub", "{\"project\":\"pay\",\"stub\":{\"name\":\"Refund\",\"request\":{\"method\":\"POST\",\"urlPath\":\"/refunds\"},\"response\":{\"status\":201}},\"summary\":\"Add refunds\"}");
    JsonNode refused = tool(secret, "clear_requests", "{\"project\":\"pay\"}");
    int beforeApply = server.api().post("/pay/refunds", "{}").status();
    JsonNode pending = admin.get(API + "/projects/" + payId + "/staged").json();
    String changeId = pending.at("/0/change/id").asText();
    JsonNode applied = admin.post(API + "/projects/" + payId + "/staged/apply", "{\"ids\":[\"" + changeId + "\"]}").json();

    // Then
    assertThat(content(staged).get("staged").asBoolean()).isTrue();
    assertThat(refused.get("isError").asBoolean()).isTrue();
    assertThat(refused.at("/content/0/text").asText()).contains("stages changes for review");
    assertThat(beforeApply).isEqualTo(404);
    assertThat(pending).singleElement().satisfies(s -> {
      assertThat(s.get("by").asText()).isEqualTo("admin via token “Cursor”");
      assertThat(s.at("/change/summary").asText()).isEqualTo("Add refunds");
    });
    assertThat(applied.get("applied").asInt()).isEqualTo(1);
    assertThat(server.api().post("/pay/refunds", "{}").status()).isEqualTo(201);
    assertThat(admin.get(API + "/projects/" + payId + "/staged").json()).isEmpty();
  }

  private static final String PETS = String.join("\n", "openapi: 3.0.3", "info: {title: Pet Store, version: '1.0'}", "paths:",
      "  /pets:", "    get:", "      responses:", "        '200':", "          description: ok",
      "          content: {application/json: {example: [{id: 1, name: Rex}]}}");
  private static final String CART = String.join("\n", "syntax = \"proto3\";", "package shop;",
      "message GetCartRequest { string id = 1; }", "message Cart { string id = 1; int32 items = 2; }",
      "service Carts { rpc GetCart (GetCartRequest) returns (Cart); }");

  @Test
  void anAgentSetsUpAWholeProjectFromOneConversation() throws Exception {
    // Given
    String secret = token("{\"name\":\"Claude Code\",\"role\":\"manager\"}");

    // When
    JsonNode project = content(tool(secret, "create_project", "{\"name\":\"Pet Store\",\"description\":\"From the spec\"}"));
    JsonNode imported = content(tool(secret, "import_spec", Jsons.write(Jsons.object().put("project", "pet-store").put("format", "openapi")
        .put("content", PETS).put("keep_contract", true))));
    int pets = server.api().get("/pet-store/pets").status();
    JsonNode grpc = tool(secret, "add_grpc_service", Jsons.write(Jsons.object().put("project", "pet-store").put("name", "cart.proto").put("proto", CART)));
    JsonNode crud = tool(secret, "create_crud_resource", "{\"project\":\"pet-store\",\"resource\":\"owners\",\"seed\":[{\"name\":\"Ada\"}]}");
    JsonNode renamed = content(tool(secret, "update_project", "{\"project\":\"pet-store\",\"name\":\"Pets\",\"description\":\"Mocked by an agent\"}"));
    JsonNode dataset = tool(secret, "save_dataset", "{\"project\":\"pet-store\",\"key\":\"breeds\",\"records\":[{\"id\":\"b1\",\"name\":\"Collie\"}]}");
    JsonNode datasets = content(tool(secret, "list_datasets", "{\"project\":\"pet-store\"}"));
    JsonNode drift = content(tool(secret, "get_drift", "{\"project\":\"pet-store\"}"));
    JsonNode apiKey = tool(secret, "update_project", "{\"project\":\"pet-store\",\"api_key\":\"s3cret\"}");

    // Then
    assertThat(project.get("slug").asText()).isEqualTo("pet-store");
    assertThat(imported.get("imported").asInt()).isEqualTo(1);
    assertThat(imported.get("contract").asBoolean()).isTrue();
    assertThat(pets).isEqualTo(200);
    assertThat(grpc.get("isError").asBoolean()).as(grpc.toString()).isFalse();
    assertThat(admin.get(API + "/projects/" + project.get("id").asText() + "/grpc").body()).contains("shop.Carts");
    assertThat(crud.get("isError").asBoolean()).as(crud.toString()).isFalse();
    assertThat(server.api().get("/pet-store/owners").body()).contains("Ada");
    assertThat(renamed.get("name").asText()).isEqualTo("Pets");
    assertThat(renamed.get("description").asText()).isEqualTo("Mocked by an agent");
    assertThat(dataset.get("isError").asBoolean()).as(dataset.toString()).isFalse();
    assertThat(datasets.toString()).contains("breeds");
    assertThat(drift.get("attached").asBoolean()).isTrue();
    assertThat(apiKey.get("isError").asBoolean()).isTrue();
    assertThat(apiKey.at("/content/0/text").asText()).contains("Only admins");
  }

  @Test
  void aReviewTokenStagesAWholeImportAndAGrpcServiceForApproval() throws Exception {
    // Given
    String secret = token("{\"name\":\"Cursor\",\"role\":\"editor\",\"mode\":\"review\"}");

    // When
    JsonNode staged = tool(secret, "import_spec", Jsons.write(Jsons.object().put("project", "pay").put("format", "openapi").put("content", PETS)
        .put("keep_contract", true)));
    JsonNode proto = tool(secret, "add_grpc_service", Jsons.write(Jsons.object().put("project", "pay").put("name", "cart.proto").put("proto", CART)));
    JsonNode badProto = tool(secret, "add_grpc_service", "{\"project\":\"pay\",\"name\":\"cart.proto\",\"proto\":\"service {\"}");
    JsonNode project = tool(secret, "create_project", "{\"name\":\"Nope\"}");
    int beforeApply = server.api().get("/pay/pets").status();
    JsonNode pending = admin.get(API + "/projects/" + payId + "/staged").json();
    List<String> ids = new ArrayList<>();
    pending.forEach(p -> ids.add("\"" + p.at("/change/id").asText() + "\""));
    JsonNode applied = admin.post(API + "/projects/" + payId + "/staged/apply", "{\"ids\":" + ids + "}").json();

    // Then
    assertThat(staged.get("isError").asBoolean()).as(staged.toString()).isFalse();
    assertThat(proto.get("isError").asBoolean()).as(proto.toString()).isFalse();
    assertThat(badProto.get("isError").asBoolean()).isTrue();
    assertThat(project.at("/content/0/text").asText()).contains("stages changes for review");
    assertThat(beforeApply).isEqualTo(404);
    List<String> actions = new ArrayList<>();
    pending.forEach(p -> actions.add(p.at("/change/action").asText()));
    assertThat(actions).containsExactlyInAnyOrder("create", "contract", "grpc");
    assertThat(applied.get("applied").asInt()).as(applied.toString()).isEqualTo(3);
    assertThat(server.api().get("/pay/pets").status()).isEqualTo(200);
    assertThat(admin.get(API + "/projects/" + payId + "/contract").json().get("attached").asBoolean()).isTrue();
    assertThat(admin.get(API + "/projects/" + payId + "/grpc").body()).contains("shop.Carts");
  }

  @Test
  void anAgentTakesASnapshotAndRollsBackAfterABadChange() throws Exception {
    // Given
    String secret = token("{\"name\":\"Claude Code\",\"role\":\"editor\"}");
    tool(secret, "create_stub", "{\"project\":\"shop\",\"stub\":{\"name\":\"Items\",\"request\":{\"method\":\"GET\",\"urlPath\":\"/items\"},\"response\":{\"status\":200}}}");
    JsonNode snapshot = content(tool(secret, "create_snapshot", "{\"project\":\"shop\",\"name\":\"before retries\",\"purpose\":\"try retry rules\"}"));
    String stubId = content(tool(secret, "list_stubs", "{\"project\":\"shop\"}")).at("/0/id").asText();
    tool(secret, "delete_stub", "{\"project\":\"shop\",\"stub_id\":\"" + stubId + "\"}");

    // When
    JsonNode diff = content(tool(secret, "compare_snapshots", "{\"project\":\"shop\",\"from\":\"" + snapshot.get("id").asText() + "\"}"));
    JsonNode restored = content(tool(secret, "restore_snapshot", "{\"project\":\"shop\",\"snapshot_id\":\"" + snapshot.get("id").asText() + "\"}"));
    JsonNode listed = content(tool(secret, "list_snapshots", "{\"project\":\"shop\"}"));

    // Then
    assertThat(diff.at("/counts/removed").asInt()).isEqualTo(1);
    assertThat(diff.at("/stubs/0").has("before")).isFalse();
    assertThat(restored.get("stubs").asInt()).isEqualTo(1);
    assertThat(server.api().get("/shop/items").status()).isEqualTo(200);
    assertThat(listed).hasSize(2);
    assertThat(listed.get(1).get("createdBy").asText()).isEqualTo("admin via token “Claude Code”");
    assertThat(admin.get(API + "/projects/" + shopId + "/audit").json().toString()).contains("restored").contains("before retries");
  }

  @Test
  void aReviewTokenStagesARestoreAndCannotPull() throws Exception {
    // Given
    admin.post(API + "/projects/" + payId + "/stubs", "{\"name\":\"Status\",\"request\":{\"method\":\"GET\",\"urlPath\":\"/status\"},\"response\":{\"status\":200}}");
    String v1 = admin.post(API + "/projects/" + payId + "/snapshots", "{\"name\":\"v1\"}").json().get("id").asText();
    admin.post(API + "/projects/" + payId + "/stubs", "{\"name\":\"Extra\",\"request\":{\"method\":\"GET\",\"urlPath\":\"/extra\"},\"response\":{\"status\":200}}");
    String secret = token("{\"name\":\"Cursor\",\"role\":\"editor\",\"mode\":\"review\"}");

    // When
    JsonNode staged = tool(secret, "restore_snapshot", "{\"project\":\"pay\",\"snapshot_id\":\"" + v1 + "\",\"summary\":\"Back to v1\"}");
    JsonNode pull = tool(secret, "pull_from_project", "{\"project\":\"pay\",\"from_project\":\"shop\"}");
    int extraBefore = server.api().get("/pay/extra").status();
    String changeId = admin.get(API + "/projects/" + payId + "/staged").json().at("/0/change/id").asText();
    admin.post(API + "/projects/" + payId + "/staged/apply", "{\"ids\":[\"" + changeId + "\"]}");

    // Then
    assertThat(content(staged).at("/change/staged").asText()).isEqualTo("restore");
    assertThat(pull.get("isError").asBoolean()).isTrue();
    assertThat(extraBefore).isEqualTo(200);
    assertThat(server.api().get("/pay/extra").status()).isEqualTo(404);
    assertThat(server.api().get("/pay/status").status()).isEqualTo(200);
  }

  @Test
  void anAgentPullsOneProjectIntoAnother() throws Exception {
    // Given
    String secret = token("{\"name\":\"Codex\",\"role\":\"editor\"}");
    admin.post(API + "/projects/" + shopId + "/stubs", "{\"name\":\"Items\",\"request\":{\"method\":\"GET\",\"urlPath\":\"/items\"},\"response\":{\"status\":200}}");

    // When
    JsonNode pulled = tool(secret, "pull_from_project", "{\"project\":\"pay\",\"from_project\":\"Shop\"}");

    // Then
    assertThat(pulled.get("isError").asBoolean()).as(pulled.toString()).isFalse();
    assertThat(server.api().get("/pay/items").status()).isEqualTo(200);
  }

  @Test
  void aProjectTokenOnlySeesItsProject() throws Exception {
    // Given
    String secret = token("{\"name\":\"Shop only\",\"role\":\"editor\",\"projectIds\":[\"" + shopId + "\"]}");

    // When
    JsonNode projects = content(tool(secret, "list_projects", "{}"));
    JsonNode other = tool(secret, "list_stubs", "{\"project\":\"pay\"}");
    int restOther = server.api().bearer(secret).get(API + "/projects/" + payId + "/stubs").status();

    // Then
    assertThat(projects).singleElement().satisfies(p -> assertThat(p.get("name").asText()).isEqualTo("Shop"));
    assertThat(other.get("isError").asBoolean()).isTrue();
    assertThat(restOther).isEqualTo(403);
  }

  @Test
  void refusesCallsWithoutCredentialsAndAnswersOnlyPost() {
    // When
    int anonymous = server.api().post(MCP, "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"ping\"}").status();
    int stream = admin.get(MCP).status();
    int notification = admin.post(MCP, "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}").status();
    JsonNode unknown = admin.post(MCP, "{\"jsonrpc\":\"2.0\",\"id\":7,\"method\":\"resources/list\"}").json();
    int foreign = admin.send("POST", MCP, "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"ping\"}", "application/json", Map.of("Origin", "https://evil.example")).status();

    // Then
    assertThat(anonymous).isEqualTo(401);
    assertThat(stream).isEqualTo(405);
    assertThat(notification).isEqualTo(202);
    assertThat(unknown.at("/error/code").asInt()).isEqualTo(-32601);
    assertThat(foreign).isEqualTo(403);
  }
}
