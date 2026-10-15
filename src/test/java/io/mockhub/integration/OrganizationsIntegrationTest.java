package io.mockhub.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OrganizationsIntegrationTest {

  private static final String API = "/__admin/mockhub/api";

  @TempDir
  Path data;
  private MockHubServer server;
  private ApiClient admin;

  @BeforeEach
  void start() {
    server = MockHubServer.start(data, Map.of("MOCKHUB_USERS", "root:pw:admin"));
    admin = server.api().login("root", "pw");
  }

  @AfterEach
  void stop() {
    server.close();
  }

  private String createUser(String username) {
    return admin.post(API + "/users", "{\"username\":\"" + username + "\",\"password\":\"longenough\"}").json().get("id").asText();
  }

  private String createProject(ApiClient client, String name, String orgId) {
    ApiClient.Result r = client.post(API + "/projects", "{\"name\":\"" + name + "\",\"orgId\":\"" + orgId + "\"}");
    assertThat(r.status()).as(r.body()).isEqualTo(201);
    return r.json().get("id").asText();
  }

  private static boolean containsName(JsonNode list, String name) {
    return StreamSupport.stream(list.spliterator(), false).anyMatch(n -> n.path("name").asText().equals(name));
  }

  @Test
  void organisationsSeparateProjectsAndMembersGetTheirRole() {
    // Given
    String defaultOrg = admin.get(API + "/orgs").json().get(0).get("id").asText();
    String payments = admin.post(API + "/orgs", "{\"name\":\"Payments\"}").json().get("id").asText();
    String anaId = createUser("ana");
    String qaId = createUser("qa");
    admin.put(API + "/orgs/" + payments + "/members/" + anaId, "{\"role\":\"owner\"}");
    String paymentsProject = createProject(admin, "Cards", payments);
    String defaultProject = createProject(admin, "Legacy", defaultOrg);

    // When
    ApiClient ana = server.api().login("ana", "longenough");
    ApiClient.Result added = ana.put(API + "/orgs/" + payments + "/members/" + qaId, "{\"role\":\"editor\"}");
    ApiClient qa = server.api().login("qa", "longenough");

    // Then
    assertThat(added.status()).isEqualTo(200);
    JsonNode me = ana.get(API + "/auth/me").json();
    assertThat(me.get("orgs")).hasSize(1);
    assertThat(me.at("/orgs/0/role").asText()).isEqualTo("owner");
    assertThat(containsName(ana.get(API + "/projects").json(), "Cards")).isTrue();
    assertThat(containsName(ana.get(API + "/projects").json(), "Legacy")).isFalse();
    assertThat(qa.post(API + "/projects/" + paymentsProject + "/stubs", "{\"request\":{\"urlPath\":\"/x\"}}").status()).isEqualTo(201);
    assertThat(qa.get(API + "/projects/" + defaultProject).status()).isEqualTo(403);
    assertThat(qa.put(API + "/orgs/" + payments, "{\"name\":\"Mine\"}").status()).isEqualTo(403);
    assertThat(ana.post(API + "/projects", "{\"name\":\"Sneaky\",\"orgId\":\"" + defaultOrg + "\"}").status()).isEqualTo(403);
    assertThat(ana.post(API + "/orgs", "{\"name\":\"Another\"}").status()).isEqualTo(403);
    assertThat(ana.get(API + "/sso").status()).isEqualTo(403);
    assertThat(ana.get(API + "/users").json().get(0).has("memberships")).isFalse();
    assertThat(admin.get(API + "/orgs/" + payments + "/members").json()).hasSize(2);
  }

  @Test
  void ownersCannotDemoteThemselves() {
    // Given
    String payments = admin.post(API + "/orgs", "{\"name\":\"Payments\"}").json().get("id").asText();
    String anaId = createUser("ana");
    admin.put(API + "/orgs/" + payments + "/members/" + anaId, "{\"role\":\"owner\"}");
    ApiClient ana = server.api().login("ana", "longenough");

    // Then
    assertThat(ana.delete(API + "/orgs/" + payments + "/members/" + anaId).status()).isEqualTo(400);
    assertThat(ana.put(API + "/orgs/" + payments + "/members/" + anaId, "{\"role\":\"viewer\"}").status()).isEqualTo(400);
  }

  @Test
  void deletingAnOrganisationDeletesItsProjects() {
    // Given
    String payments = admin.post(API + "/orgs", "{\"name\":\"Payments\"}").json().get("id").asText();
    String project = createProject(admin, "Cards", payments);

    // When
    ApiClient.Result deleted = admin.delete(API + "/orgs/" + payments);

    // Then
    assertThat(deleted.status()).isEqualTo(204);
    assertThat(admin.get(API + "/projects/" + project).status()).isEqualTo(404);
  }

  @Test
  void changingAPasswordSignsOutOtherSessions() throws Exception {
    // Given
    createUser("ana");
    ApiClient old = server.api().login("ana", "longenough");
    Thread.sleep(5);

    // When
    ApiClient.Result changed = old.post(API + "/auth/password", "{\"currentPassword\":\"longenough\",\"newPassword\":\"brandnewpw\"}");

    // Then
    assertThat(changed.status()).isEqualTo(200);
    assertThat(old.get(API + "/auth/me").status()).isEqualTo(401);
    assertThat(server.api().bearer(changed.json().get("token").asText()).get(API + "/auth/me").status()).isEqualTo(200);
  }

  @Test
  void disabledUsersAreSignedOut() {
    // Given
    String anaId = createUser("ana");
    ApiClient ana = server.api().login("ana", "longenough");

    // When
    admin.put(API + "/users/" + anaId, "{\"disabled\":true}");

    // Then
    assertThat(ana.get(API + "/auth/me").status()).isEqualTo(401);
    assertThat(server.api().post(API + "/auth/login", "{\"username\":\"ana\",\"password\":\"longenough\"}").status()).isEqualTo(401);
  }

  @Test
  void theFirstUserBecomesAdminAndTurnsSignInOn(@TempDir Path fresh) {
    // Given
    server.close();
    server = MockHubServer.start(fresh);
    ApiClient anonymous = server.api();
    assertThat(anonymous.get(API + "/auth/info").json().get("authEnabled").asBoolean()).isFalse();

    // When
    ApiClient.Result created = anonymous.post(API + "/users", "{\"username\":\"owner\",\"password\":\"longenough\",\"serverRole\":\"viewer\"}");

    // Then
    assertThat(created.status()).isEqualTo(201);
    assertThat(created.json().get("serverRole").asText()).isEqualTo("admin");
    assertThat(anonymous.get(API + "/projects").status()).isEqualTo(401);
    assertThat(server.api().bearer(created.json().get("token").asText()).get(API + "/projects").status()).isEqualTo(200);
  }
}
