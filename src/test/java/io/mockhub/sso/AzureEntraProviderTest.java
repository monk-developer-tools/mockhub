package io.mockhub.sso;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.mockhub.api.Jsons;
import io.mockhub.auth.Role;
import io.mockhub.support.FakeHttpTransport;
import io.mockhub.support.MutableClock;
import io.mockhub.support.TestIdp;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class AzureEntraProviderTest {

  private static final String AUTHORITY = "https://login.microsoftonline.com";
  private static final String BASE = AUTHORITY + "/" + TestIdp.TENANT;

  private final MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
  private final TestIdp idp = new TestIdp();
  private final FakeHttpTransport http = new FakeHttpTransport().onGet(BASE + "/discovery/v2.0/keys", idp.jwks());
  private final AzureEntraProvider provider = new AzureEntraProvider(http, new JwtVerifier(http, clock), "");
  private final SsoSettings settings = new SsoSettings(true, "azure", TestIdp.TENANT, TestIdp.CLIENT, "s3cret", "global", null,
      List.of(), Role.NONE, "", Role.NONE, List.of(), true);
  private final SsoProvider.AuthorizationRequest request =
      new SsoProvider.AuthorizationRequest("https://mock.example/cb", "state-1", "nonce-1", "verifier-1", "challenge-1");

  @Test
  void buildsTheAuthorizeUrlWithPkce() {
    // When
    String url = provider.authorizationUrl(settings, request);

    // Then
    assertThat(url).startsWith(BASE + "/oauth2/v2.0/authorize?")
        .contains("client_id=" + TestIdp.CLIENT)
        .contains("redirect_uri=https%3A%2F%2Fmock.example%2Fcb")
        .contains("state=state-1", "nonce=nonce-1", "code_challenge=challenge-1", "code_challenge_method=S256")
        .contains("scope=openid+profile+email");
  }

  @Test
  void usesTheSovereignCloudHost() {
    // When
    String url = provider.authorizationUrl(new SsoSettings(true, "azure", "t", "c", "s", "usgov", null, List.of(), Role.NONE, "", Role.NONE, List.of(), true), request);

    // Then
    assertThat(url).startsWith("https://login.microsoftonline.us/t/oauth2/v2.0/authorize");
  }

  @Test
  void exchangesTheCodeForAVerifiedIdentity() {
    // Given
    var claims = idp.claims(AUTHORITY, "nonce-1", clock.millis() / 1000);
    claims.putArray("groups").add("g-qa");
    claims.putArray("roles").add("MockHub.Admin");
    http.onPost(BASE + "/oauth2/v2.0/token", form -> Jsons.object().put("id_token", idp.sign(claims)));

    // When
    ExternalIdentity identity = provider.exchange(settings, "code-1", request);

    // Then
    assertThat(identity.subject()).isEqualTo("user-oid-1");
    assertThat(identity.username()).isEqualTo("ana@contoso.com");
    assertThat(identity.name()).isEqualTo("Ana Tester");
    assertThat(identity.tenantId()).isEqualTo(TestIdp.TENANT);
    assertThat(identity.groups()).containsExactly("g-qa");
    assertThat(identity.roles()).containsExactly("MockHub.Admin");
    assertThat(http.postedForms.get(0))
        .containsEntry("code", "code-1")
        .containsEntry("code_verifier", "verifier-1")
        .containsEntry("client_secret", "s3cret")
        .containsEntry("grant_type", "authorization_code");
  }

  @Test
  void rejectsTokensFromAnotherTenant() {
    // Given
    var claims = idp.claims(AUTHORITY, "nonce-1", clock.millis() / 1000)
        .put("tid", "99999999-9999-9999-9999-999999999999")
        .put("iss", AUTHORITY + "/99999999-9999-9999-9999-999999999999/v2.0");
    http.onPost(BASE + "/oauth2/v2.0/token", form -> Jsons.object().put("id_token", idp.sign(claims)));

    // Then
    assertThatThrownBy(() -> provider.exchange(settings, "code-1", request)).hasMessageContaining("issuer");
  }

  @Test
  void testsTheConnectionThroughOpenIdDiscovery() {
    // Given
    http.onGet(BASE + "/v2.0/.well-known/openid-configuration", Jsons.object()
        .put("issuer", BASE + "/v2.0").put("authorization_endpoint", BASE + "/oauth2/v2.0/authorize").put("token_endpoint", "t"));

    // When
    var result = provider.testConnection(settings);

    // Then
    assertThat(result.path("issuer").asText()).isEqualTo(BASE + "/v2.0");
  }
}
