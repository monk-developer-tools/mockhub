package io.mockhub.publictools;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.tomakehurst.wiremock.http.ResponseDefinition;
import io.mockhub.api.Jsons;
import io.mockhub.auth.Principal;
import io.mockhub.auth.Role;
import io.mockhub.runtime.RateLimiter;
import io.mockhub.support.MutableClock;
import io.mockhub.user.User;
import io.mockhub.user.UserSource;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BinDraftTest {
  private static final String RULE = "{\"name\": \"Refunds fail\", \"method\": \"POST\", \"answers\": [{\"status\": 500, \"body\": \"no\"}]}";

  @TempDir
  Path data;
  private final MutableClock clock = new MutableClock(Instant.parse("2026-11-06T10:00:00Z"));
  private Bins bins;
  private Bins.Created bin;
  private BinTool tool;

  @BeforeEach
  void setUp() {
    bins = new Bins(data, clock);
    bin = bins.create().orElseThrow();
    Principal alex = Principal.of(new User.Builder().id("u1").username("alex").source(UserSource.LOCAL).serverRole(Role.VIEWER).build());
    tool = new BinTool(bins, new PublicLimits(new RateLimiter(clock)), new BinResponder(null, null, null, null, null, bound -> 0, System::currentTimeMillis),
        new BinDrafter(() -> true, (system, user) -> "```json\n" + RULE + "\n```"), name -> Optional.empty(),
        header -> "Bearer signed-in".equals(header) ? Optional.of(alex) : Optional.empty(), System::currentTimeMillis);
  }

  private ResponseDefinition draft(Map<String, String> headers) {
    return tool.handle(new PublicRequest("POST", "/inspect/" + bin.id() + "/draft", "", Map.of(), headers,
        "{\"description\": \"fail every refund\"}", 30, "1.2.3.4")).orElseThrow();
  }

  private static JsonNode json(ResponseDefinition response) throws Exception {
    return Jsons.MAPPER.readTree(response.getBody());
  }

  @Test
  void anonymousVisitorsCannotDraftRulesWithAi() throws Exception {
    // Given
    Map<String, String> headers = Map.of("X-Bin-Token", bin.token());

    // When
    ResponseDefinition response = draft(headers);

    // Then
    assertThat(response.getStatus()).isEqualTo(401);
    assertThat(json(response).get("error").asText()).contains("Sign in");
  }

  @Test
  void signedInUsersGetADraftRuleThatIsNotSaved() throws Exception {
    // Given
    Map<String, String> headers = Map.of("X-Bin-Token", bin.token(), "Authorization", "Bearer signed-in");

    // When
    ResponseDefinition response = draft(headers);

    // Then
    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(json(response).at("/rule/name").asText()).isEqualTo("Refunds fail");
    assertThat(bins.settings(bin.id(), bin.token()).orElseThrow().rules()).isEmpty();
  }

  @Test
  void signingInDoesNotOpenSomeoneElsesBin() throws Exception {
    // Given
    Map<String, String> headers = Map.of("X-Bin-Token", "a".repeat(32), "Authorization", "Bearer signed-in");

    // When
    ResponseDefinition response = draft(headers);

    // Then
    assertThat(response.getStatus()).isEqualTo(404);
  }
}
