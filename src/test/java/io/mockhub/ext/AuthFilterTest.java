package io.mockhub.ext;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.http.RequestMethod;
import io.mockhub.Config.AuthUser;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class AuthFilterTest {

  private final AuthFilter filter = new AuthFilter(List.of(
      new AuthUser("alice", "secret", "admin"),
      new AuthUser("bob", "sha256:2bb80d537b1da3e38bd30361aa855686bde0eacd7162fef6a25fe97bf527a25b", "viewer")));

  @Test
  void authenticatesPlainAndHashedPasswords() {
    assertThat(filter.authenticate(basic("alice", "secret"))).hasValueSatisfying(u -> assertThat(u.role()).isEqualTo("admin"));
    assertThat(filter.authenticate(basic("bob", "secret"))).hasValueSatisfying(u -> assertThat(u.role()).isEqualTo("viewer"));
  }

  @Test
  void rejectsWrongCredentialsAndMalformedHeaders() {
    assertThat(filter.authenticate(basic("alice", "nope"))).isEmpty();
    assertThat(filter.authenticate("Bearer abc")).isEmpty();
    assertThat(filter.authenticate("Basic !!!")).isEmpty();
    assertThat(filter.authenticate(null)).isEmpty();
  }

  @Test
  void isDisabledWithoutUsers() {
    assertThat(new AuthFilter(List.of()).enabled()).isFalse();
    assertThat(filter.enabled()).isTrue();
  }

  @ParameterizedTest
  @CsvSource({
      "viewer, GET, /mockhub/api/projects, true",
      "viewer, POST, /mockhub/api/projects, false",
      "viewer, POST, /requests/find, true",
      "viewer, DELETE, /mappings/abc, false",
      "editor, POST, /mockhub/api/projects, true",
      "editor, DELETE, /mockhub/api/projects/x, true",
      "editor, POST, /reset, false",
      "editor, POST, /shutdown, false",
      "editor, DELETE, /mappings, false",
      "editor, POST, /settings, false",
      "editor, GET, /settings, true",
      "admin, POST, /shutdown, true",
      "admin, DELETE, /mappings, true"
  })
  void enforcesRolePermissions(String role, String method, String path, boolean allowed) {
    assertThat(AuthFilter.allowed(role, RequestMethod.fromString(method), path)).isEqualTo(allowed);
  }

  @Test
  void normalisesAdminPaths() {
    assertThat(AuthFilter.adminPath("/__admin/mappings/?limit=1")).isEqualTo("/mappings");
    assertThat(AuthFilter.adminPath("/mockhub/ui/assets/x.js")).isEqualTo("/mockhub/ui/assets/x.js");
  }

  private static String basic(String user, String password) {
    return "Basic " + Base64.getEncoder().encodeToString((user + ":" + password).getBytes(StandardCharsets.UTF_8));
  }
}
