package io.mockhub.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import io.mockhub.support.Fixtures;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CorsRulesTest {

  @Test
  void matchesExactAndWildcardSubdomainOrigins() {
    // Given
    CorsRules rules = CorsRules.parse(Fixtures.json("{\"origins\":[\"https://app.example\",\"https://*.acme.test\"]}"));

    // When
    boolean exact = rules.allowsOrigin("https://app.example");
    boolean sub = rules.allowsOrigin("https://shop.acme.test");
    boolean bare = rules.allowsOrigin("https://acme.test");
    boolean otherScheme = rules.allowsOrigin("http://shop.acme.test");

    // Then
    assertThat(exact).isTrue();
    assertThat(sub).isTrue();
    assertThat(bare).isFalse();
    assertThat(otherScheme).isFalse();
  }

  @Test
  void anyOriginWithoutCredentialsAnswersWithAStar() {
    // Given
    CorsHeaders headers = new CorsHeaders(false);
    CorsRules rules = new CorsRules(List.of("*"), List.of(), List.of(), false, false);

    // When
    Map<String, String> response = headers.forResponse(rules, "https://x.example");

    // Then
    assertThat(response).containsEntry("Access-Control-Allow-Origin", "*").doesNotContainKey("Vary");
  }

  @Test
  void projectsWithoutRulesFollowTheServerDefault() {
    // Given
    CorsHeaders permissive = new CorsHeaders(true);
    CorsHeaders strict = new CorsHeaders(false);

    // When
    Map<String, String> open = permissive.forPreflight(permissive.rulesFor(null), "https://x.example", "content-type");
    Map<String, String> closed = strict.forResponse(strict.rulesFor(null), "https://x.example");

    // Then
    assertThat(open).containsEntry("Access-Control-Allow-Origin", "https://x.example")
        .containsEntry("Access-Control-Allow-Credentials", "true")
        .containsEntry("Access-Control-Allow-Headers", "content-type");
    assertThat(closed).isEmpty();
  }
}
