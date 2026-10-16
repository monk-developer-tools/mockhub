package io.mockhub.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.mockhub.api.Jsons;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TeamsIntegrationTest {

  private static final String API = "/__admin/mockhub/api";

  @TempDir
  Path data;
  private MockHubServer server;
  private ApiClient admin;
  private String orgId;

  @BeforeEach
  void start() {
    server = MockHubServer.start(data, Map.of("MOCKHUB_USERS", "root:pw:admin"));
    admin = server.api().login("root", "pw");
    orgId = admin.post(API + "/orgs", "{\"name\":\"Payments\"}").json().get("id").asText();
  }

  @AfterEach
  void stop() {
    server.close();
  }

  private String team(String name) {
    ApiClient.Result r = admin.post(API + "/orgs/" + orgId + "/teams", "{\"name\":\"" + name + "\"}");
    assertThat(r.status()).as(r.body()).isEqualTo(201);
    return r.json().get("id").asText();
  }

  private static boolean hasName(JsonNode list, String name) {
    return StreamSupport.stream(list.spliterator(), false).anyMatch(n -> n.path("name").asText().equals(name));
  }

  @Test
  void newOrganisationsStartWithAGeneralTeam() {
    // When
    JsonNode teams = admin.get(API + "/orgs/" + orgId + "/teams").json();

    // Then
    assertThat(teams).hasSize(1);
    assertThat(teams.get(0).get("name").asText()).isEqualTo("General");
    assertThat(admin.get(API + "/orgs/" + orgId).json().get("teamCount").asInt()).isEqualTo(1);
  }

  @Test
  void teamMembersSeeAndChangeOnlyTheirTeamsProjects() {
    // Given
    String checkout = team("Checkout");
    String search = team("Search");
    admin.post(API + "/users", "{\"username\":\"dev\",\"password\":\"longenough\",\"teamMemberships\":[{\"teamId\":\"" + checkout + "\",\"role\":\"editor\"}]}");
    String cart = admin.post(API + "/projects", "{\"name\":\"Cart\",\"teamId\":\"" + checkout + "\"}").json().get("id").asText();
    String index = admin.post(API + "/projects", "{\"name\":\"Index\",\"teamId\":\"" + search + "\"}").json().get("id").asText();
    ApiClient dev = server.api().login("dev", "longenough");

    // When
    JsonNode visible = dev.get(API + "/projects").json();
    ApiClient.Result own = dev.post(API + "/projects", "{\"name\":\"Basket\",\"teamId\":\"" + checkout + "\"}");
    ApiClient.Result foreign = dev.post(API + "/projects", "{\"name\":\"Ranker\",\"teamId\":\"" + search + "\"}");
    JsonNode me = dev.get(API + "/auth/me").json();

    // Then
    assertThat(hasName(visible, "Cart")).isTrue();
    assertThat(hasName(visible, "Index")).isFalse();
    assertThat(own.status()).isEqualTo(201);
    assertThat(own.json().get("teamId").asText()).isEqualTo(checkout);
    assertThat(foreign.status()).isEqualTo(403);
    assertThat(dev.post(API + "/projects/" + cart + "/stubs", "{\"request\":{\"method\":\"GET\",\"url\":\"/x\"}}").status()).isEqualTo(201);
    assertThat(dev.get(API + "/projects/" + index).status()).isEqualTo(403);
    assertThat(me.get("orgs")).hasSize(1);
    assertThat(me.at("/orgs/0/role").asText()).isEqualTo("none");
    JsonNode teams = me.at("/orgs/0/teams");
    assertThat(StreamSupport.stream(teams.spliterator(), false).filter(t -> t.get("id").asText().equals(checkout)).findFirst().orElseThrow()
        .get("role").asText()).isEqualTo("editor");
  }

  @Test
  void teamOwnersManageTheirTeamButNotOthers() {
    // Given
    String checkout = team("Checkout");
    String search = team("Search");
    String leadId = admin.post(API + "/users", "{\"username\":\"lead\",\"password\":\"longenough\"}").json().get("id").asText();
    String newbieId = admin.post(API + "/users", "{\"username\":\"newbie\",\"password\":\"longenough\"}").json().get("id").asText();
    admin.put(API + "/orgs/" + orgId + "/teams/" + checkout + "/members/" + leadId, "{\"role\":\"owner\"}");
    ApiClient lead = server.api().login("lead", "longenough");

    // When
    ApiClient.Result add = lead.put(API + "/orgs/" + orgId + "/teams/" + checkout + "/members/" + newbieId, "{\"role\":\"viewer\"}");
    ApiClient.Result other = lead.put(API + "/orgs/" + orgId + "/teams/" + search + "/members/" + newbieId, "{\"role\":\"viewer\"}");
    ApiClient.Result rename = lead.put(API + "/orgs/" + orgId + "/teams/" + checkout, "{\"description\":\"Cart and payment pages\"}");
    ApiClient.Result delete = lead.delete(API + "/orgs/" + orgId + "/teams/" + checkout);
    JsonNode members = lead.get(API + "/orgs/" + orgId + "/teams/" + checkout + "/members").json();

    // Then
    assertThat(add.status()).as(add.body()).isEqualTo(200);
    assertThat(other.status()).isEqualTo(403);
    assertThat(rename.json().get("description").asText()).isEqualTo("Cart and payment pages");
    assertThat(delete.status()).isEqualTo(403);
    assertThat(StreamSupport.stream(members.spliterator(), false).filter(m -> m.get("username").asText().equals("newbie"))
        .findFirst().orElseThrow().get("role").asText()).isEqualTo("viewer");
  }

  @Test
  void teamsWithProjectsCannotBeDeletedAndEmptyOnesCan() {
    // Given
    String checkout = team("Checkout");
    String empty = team("Empty");
    String cart = admin.post(API + "/projects", "{\"name\":\"Cart\",\"teamId\":\"" + checkout + "\"}").json().get("id").asText();

    // When
    ApiClient.Result blocked = admin.delete(API + "/orgs/" + orgId + "/teams/" + checkout);
    ApiClient.Result moved = admin.put(API + "/projects/" + cart, "{\"teamId\":\"" + empty + "\"}");
    ApiClient.Result deleted = admin.delete(API + "/orgs/" + orgId + "/teams/" + checkout);

    // Then
    assertThat(blocked.status()).isEqualTo(409);
    assertThat(blocked.json().get("error").asText()).contains("still has 1 project");
    assertThat(moved.json().get("teamId").asText()).isEqualTo(empty);
    assertThat(deleted.status()).isEqualTo(204);
  }

  @Test
  void adminsSetOrganisationAndTeamAccessFromTheUsersPage() {
    // Given
    String checkout = team("Checkout");
    String id = admin.post(API + "/users", "{\"username\":\"qa\",\"password\":\"longenough\"}").json().get("id").asText();

    // When
    JsonNode updated = admin.put(API + "/users/" + id, "{\"memberships\":[{\"orgId\":\"" + orgId + "\",\"role\":\"viewer\"}],"
        + "\"teamMemberships\":[{\"teamId\":\"" + checkout + "\",\"role\":\"editor\"}]}").json();
    ApiClient.Result unknown = admin.put(API + "/users/" + id, "{\"teamMemberships\":[{\"teamId\":\"nope\",\"role\":\"editor\"}]}");

    // Then
    assertThat(updated.at("/memberships/0/role").asText()).isEqualTo("viewer");
    assertThat(updated.at("/teamMemberships/0/teamName").asText()).isEqualTo("Checkout");
    assertThat(updated.at("/teamMemberships/0/orgId").asText()).isEqualTo(orgId);
    assertThat(unknown.status()).isEqualTo(404);
  }

  @Test
  void projectsFromBeforeTeamsMoveIntoTheGeneralTeamOnStartup() throws Exception {
    // Given
    String cart = admin.post(API + "/projects", "{\"name\":\"Cart\",\"orgId\":\"" + orgId + "\"}").json().get("id").asText();
    server.close();
    Path file = data.resolve("mockhub/projects.json");
    ArrayNode stored = (ArrayNode) Jsons.MAPPER.readTree(Files.readString(file));
    stored.forEach(p -> ((ObjectNode) p).remove("teamId"));
    Files.writeString(file, Jsons.write(stored));
    Files.deleteIfExists(data.resolve("mockhub/teams.json"));

    // When
    server = MockHubServer.start(data, Map.of("MOCKHUB_USERS", "root:pw:admin"));
    admin = server.api().login("root", "pw");

    // Then
    JsonNode teams = admin.get(API + "/orgs/" + orgId + "/teams").json();
    assertThat(teams).hasSize(1);
    assertThat(admin.get(API + "/projects/" + cart).json().get("teamId").asText()).isEqualTo(teams.get(0).get("id").asText());
  }
}
