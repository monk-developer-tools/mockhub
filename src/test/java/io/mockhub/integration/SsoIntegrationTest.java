package io.mockhub.integration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.tomakehurst.wiremock.WireMockServer;
import io.mockhub.api.Jsons;
import io.mockhub.support.TestIdp;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SsoIntegrationTest {

  private static final String API = "/__admin/mockhub/api";

  @TempDir
  Path data;
  private final TestIdp idp = new TestIdp();
  private WireMockServer entra;
  private MockHubServer server;
  private ApiClient admin;
  private String authority;

  @BeforeEach
  void start() {
    entra = new WireMockServer(options().dynamicPort());
    entra.start();
    authority = "http://localhost:" + entra.port();
    entra.stubFor(get(urlPathEqualTo("/" + TestIdp.TENANT + "/discovery/v2.0/keys"))
        .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody(Jsons.write(idp.jwks()))));
    entra.stubFor(get(urlPathEqualTo("/" + TestIdp.TENANT + "/v2.0/.well-known/openid-configuration"))
        .willReturn(aResponse().withHeader("Content-Type", "application/json")
            .withBody("{\"issuer\":\"" + authority + "/" + TestIdp.TENANT + "/v2.0\",\"authorization_endpoint\":\"a\",\"token_endpoint\":\"t\"}")));
    server = MockHubServer.start(data, Map.of("MOCKHUB_USERS", "root:pw:admin", "MOCKHUB_SSO_AUTHORITY", authority));
    admin = server.api().login("root", "pw");
  }

  @AfterEach
  void stop() {
    server.close();
    entra.stop();
  }

  private String configure(String extra) {
    String qa = admin.post(API + "/orgs", "{\"name\":\"QA\"}").json().get("id").asText();
    ApiClient.Result saved = admin.put(API + "/sso", "{\"enabled\":true,\"tenantId\":\"" + TestIdp.TENANT + "\",\"clientId\":\"" + TestIdp.CLIENT
        + "\",\"clientSecret\":\"s3cret\",\"roleMappings\":[{\"claim\":\"g-qa\",\"orgId\":\"" + qa + "\",\"role\":\"editor\"}]" + extra + "}");
    assertThat(saved.status()).as(saved.body()).isEqualTo(200);
    return qa;
  }

  private static Map<String, String> params(String query) {
    return Arrays.stream(query.split("&")).map(p -> p.split("=", 2))
        .collect(Collectors.toMap(p -> p[0], p -> URLDecoder.decode(p[1], StandardCharsets.UTF_8)));
  }

  private ApiClient.Result signIn(String... groups) {
    ApiClient.Result started = server.api().get(API + "/auth/sso/azure/start");
    assertThat(started.status()).isEqualTo(302);
    Map<String, String> authorize = params(URI.create(started.location()).getRawQuery());
    var claims = idp.claims(authority, authorize.get("nonce"), System.currentTimeMillis() / 1000);
    var list = claims.putArray("groups");
    for (String g : groups) list.add(g);
    entra.stubFor(post(urlPathEqualTo("/" + TestIdp.TENANT + "/oauth2/v2.0/token"))
        .withRequestBody(containing("code=the-code"))
        .withRequestBody(containing("code_verifier="))
        .willReturn(aResponse().withHeader("Content-Type", "application/json")
            .withBody("{\"id_token\":\"" + idp.sign(claims) + "\"}")));
    return server.api().get(API + "/auth/sso/azure/callback?code=the-code&state=" + authorize.get("state"));
  }

  @Test
  void signsInWithEntraAndMapsGroupsToOrganisationRoles() {
    // Given
    String qa = configure("");

    // When
    ApiClient.Result callback = signIn("g-qa");
    String fragment = URI.create(callback.location()).getFragment();
    ApiClient.Result exchanged = server.api().post(API + "/auth/sso/exchange", "{\"code\":\"" + fragment.substring("sso_code=".length()) + "\"}");

    // Then
    assertThat(callback.status()).isEqualTo(302);
    assertThat(callback.location()).startsWith(server.baseUrl() + "/__admin/mockhub/ui/#sso_code=");
    assertThat(exchanged.status()).as(exchanged.body()).isEqualTo(200);
    JsonNode me = server.api().bearer(exchanged.json().get("token").asText()).get(API + "/auth/me").json();
    assertThat(me.get("username").asText()).isEqualTo("ana@contoso.com");
    assertThat(me.get("source").asText()).isEqualTo("azure");
    assertThat(me.at("/orgs/0/id").asText()).isEqualTo(qa);
    assertThat(me.at("/orgs/0/role").asText()).isEqualTo("editor");
    assertThat(server.api().post(API + "/auth/sso/exchange", "{\"code\":\"" + fragment.substring(9) + "\"}").status()).isEqualTo(401);
  }

  @Test
  void showsTheSettingsWithoutTheSecretAndAdvertisesTheButton() {
    // Given
    configure(",\"buttonLabel\":\"Sign in with Contoso\"");

    // When
    JsonNode settings = admin.get(API + "/sso").json();
    JsonNode info = server.api().get(API + "/auth/info").json();

    // Then
    assertThat(settings.has("clientSecret")).isFalse();
    assertThat(settings.get("clientSecretSet").asBoolean()).isTrue();
    assertThat(settings.get("redirectUri").asText()).isEqualTo(server.baseUrl() + "/__admin/mockhub/api/auth/sso/azure/callback");
    assertThat(info.at("/sso/enabled").asBoolean()).isTrue();
    assertThat(info.at("/sso/label").asText()).isEqualTo("Sign in with Contoso");
    assertThat(admin.post(API + "/sso/test", "{}").json().get("ok").asBoolean()).isTrue();
  }

  @Test
  void reportsErrorsBackToTheLoginPage() {
    // Given
    configure(",\"allowedDomains\":[\"fabrikam.com\"]");

    // When
    ApiClient.Result denied = signIn("g-qa");
    ApiClient.Result cancelled = server.api().get(API + "/auth/sso/azure/callback?error=access_denied&error_description=User+cancelled");
    ApiClient.Result forged = server.api().get(API + "/auth/sso/azure/callback?code=x&state=forged");

    // Then
    assertThat(URLDecoder.decode(denied.location(), StandardCharsets.UTF_8)).contains("#sso_error=").contains("domain");
    assertThat(URLDecoder.decode(cancelled.location(), StandardCharsets.UTF_8)).contains("User cancelled");
    assertThat(URLDecoder.decode(forged.location(), StandardCharsets.UTF_8)).contains("expired");
  }

  @Test
  void passwordSignInCanBeTurnedOffExceptForAdmins() {
    // Given
    configure(",\"allowPasswordLogin\":false");
    admin.post(API + "/users", "{\"username\":\"ana\",\"password\":\"longenough\"}");

    // Then
    assertThat(server.api().post(API + "/auth/login", "{\"username\":\"ana\",\"password\":\"longenough\"}").status()).isEqualTo(403);
    assertThat(server.api().post(API + "/auth/login", "{\"username\":\"root\",\"password\":\"pw\"}").status()).isEqualTo(200);
    assertThat(server.api().get(API + "/auth/info").json().get("passwordLogin").asBoolean()).isFalse();
  }

  @Test
  void refusesToEnableWithoutCredentials() {
    // Then
    assertThat(admin.put(API + "/sso", "{\"enabled\":true,\"tenantId\":\"t\"}").status()).isEqualTo(400);
  }
}
