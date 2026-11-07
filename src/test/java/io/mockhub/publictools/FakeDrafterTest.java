package io.mockhub.publictools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.tomakehurst.wiremock.http.ResponseDefinition;
import io.mockhub.api.Jsons;
import io.mockhub.auth.Principal;
import io.mockhub.fake.DataGenerator;
import io.mockhub.fake.FakeData;
import io.mockhub.runtime.RateLimiter;
import io.mockhub.auth.Role;
import io.mockhub.user.User;
import io.mockhub.user.UserSource;
import java.time.Clock;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class FakeDrafterTest {

  private static final String REPLY = """
      Here you go:
      ```json
      {"count": 250, "locale": "de", "fields": [
        {"name": "id", "expression": "sequence"},
        {"name": "name", "expression": "Name.fullName"},
        {"name": "bank.iban", "expression": "Finance.iban"},
        {"name": "joined", "expression": "Date.birthday 'yyyy-MM-dd'"},
        {"name": "mood", "expression": "Nope.nothing"},
        {"name": "bad name", "expression": "Name.firstName"},
        {"name": "name", "expression": "Name.lastName"}]}
      ```""";

  private final DataGenerator generator = new DataGenerator(new FakeData("en"));
  private final AtomicReference<String> asked = new AtomicReference<>();

  private FakeDrafter drafter(String reply) {
    return new FakeDrafter(() -> true, (system, user) -> {
      asked.set(user);
      return reply;
    }, generator);
  }

  @Test
  void keepsTheFieldsThatGenerateAndClampsTheCountAndLocale() {
    // Given
    FakeDrafter drafter = drafter(REPLY);

    // When
    FakeDrafter.Draft draft = drafter.draft("  250 German customers with an IBAN  ");

    // Then
    assertThat(asked.get()).isEqualTo("250 German customers with an IBAN");
    assertThat(draft.count()).isEqualTo(100);
    assertThat(draft.locale()).isEqualTo("de");
    assertThat(draft.fields()).extracting(DataGenerator.Field::name).containsExactly("id", "name", "bank.iban", "joined");
    assertThat(draft.skipped()).containsExactly(
        "mood: Nope.nothing didn't generate a value",
        "bad name: field names are letters, digits and underscores, with dots to nest");
  }

  @Test
  void fallsBackToEnglishForAnUnknownLocaleAndRefusesWhenNothingWorks() {
    // Given
    JsonNode unknownLocale = Jsons.MAPPER.createObjectNode().put("locale", "tlh")
        .set("fields", Jsons.MAPPER.createArrayNode().add(Jsons.object().put("name", "email").put("expression", "Internet.emailAddress")));

    // When
    FakeDrafter.Draft draft = drafter("").read(unknownLocale);

    // Then
    assertThat(draft.locale()).isEmpty();
    assertThat(draft.count()).isEqualTo(10);
    assertThat(draft.skipped()).containsExactly("locale tlh: not one of the languages here, so English is used");
    assertThatThrownBy(() -> drafter("{\"fields\": [{\"name\": \"x\", \"expression\": \"Nope.nothing\"}]}").draft("anything"))
        .hasMessage("None of the AI's fields worked: x: Nope.nothing didn't generate a value");
    assertThatThrownBy(() -> drafter("no json here").draft("anything")).hasMessageContaining("didn't return any fields");
    assertThatThrownBy(() -> drafter(REPLY).draft(" ")).hasMessageContaining("Describe the records");
  }

  @Test
  void theDraftRouteNeedsAnAiProviderAndASignIn() throws Exception {
    // Given
    PublicLimits limits = new PublicLimits(new RateLimiter(Clock.systemUTC()));
    Principal ada = Principal.of(new User.Builder().id("u1").username("ada").source(UserSource.LOCAL).serverRole(Role.VIEWER).build());
    FakeTool off = new FakeTool(generator, limits, () -> false, (s, u) -> REPLY, h -> Optional.of(ada));
    FakeTool anonymous = new FakeTool(generator, limits, () -> true, (s, u) -> REPLY, h -> Optional.empty());
    FakeTool signedIn = new FakeTool(generator, limits, () -> true, (s, u) -> REPLY, h -> "Bearer t".equals(h) ? Optional.of(ada) : Optional.empty());
    String body = "{\"description\": \"German customers\"}";

    // When
    ResponseDefinition noAi = post(off, body, null);
    ResponseDefinition noUser = post(anonymous, body, null);
    ResponseDefinition drafted = post(signedIn, body, "Bearer t");
    ResponseDefinition probeOn = signedIn.handle(new PublicRequest("GET", "/fake/draft", "", Map.of(), Map.of(), "", 0, "203.0.113.9")).orElseThrow();

    // Then
    assertThat(noAi.getStatus()).isEqualTo(409);
    assertThat(noUser.getStatus()).isEqualTo(401);
    assertThat(drafted.getStatus()).isEqualTo(200);
    JsonNode json = Jsons.MAPPER.readTree(drafted.getBody());
    assertThat(json.get("locale").asText()).isEqualTo("de");
    assertThat(json.get("fields").findValuesAsText("name")).contains("bank.iban");
    assertThat(json.get("skipped")).hasSize(2);
    assertThat(Jsons.MAPPER.readTree(probeOn.getBody()).get("available").asBoolean()).isTrue();
    assertThat(Jsons.MAPPER.readTree(off.handle(new PublicRequest("GET", "/fake/draft", "", Map.of(), Map.of(), "", 0, "203.0.113.9")).orElseThrow().getBody()).get("available").asBoolean()).isFalse();
  }

  private static ResponseDefinition post(FakeTool tool, String body, String authorization) {
    Map<String, String> headers = authorization == null ? Map.of() : Map.of("Authorization", authorization);
    return tool.handle(new PublicRequest("POST", "/fake/draft", "", Map.of(), headers, body, body.length(), "203.0.113.9")).orElseThrow();
  }
}
