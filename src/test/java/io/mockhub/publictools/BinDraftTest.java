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
  private final StringBuilder asked = new StringBuilder();
  private String reply = "```json\n" + RULE + "\n```";

  @BeforeEach
  void setUp() {
    bins = new Bins(data, clock);
    bin = bins.create().orElseThrow();
    Principal alex = Principal.of(new User.Builder().id("u1").username("alex").source(UserSource.LOCAL).serverRole(Role.VIEWER).build());
    tool = new BinTool(bins, new PublicLimits(new RateLimiter(clock)), new BinResponder(null, null, null, null, null, bound -> 0, System::currentTimeMillis),
        new BinDrafter(() -> true, (system, user) -> {
          asked.append(user);
          return system.startsWith("You fix Handlebars") ? "Here you go:\n```handlebars\n{\"id\": \"{{jsonPath request.body '$.id'}}\" }\n```" : reply;
        }), name -> Optional.empty(),
        header -> "Bearer signed-in".equals(header) ? Optional.of(alex) : Optional.empty(), System::currentTimeMillis, null);
  }

  private ResponseDefinition draft(Map<String, String> headers) {
    return post("/draft", headers, "{\"description\": \"fail every refund\"}");
  }

  private ResponseDefinition post(String part, Map<String, String> headers, String body) {
    return tool.handle(new PublicRequest("POST", "/inspect/" + bin.id() + part, "", Map.of(), headers, body, body.length(), "1.2.3.4")).orElseThrow();
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

  @Test
  void oneDescriptionCanDraftSeveralRulesOfEveryKindAndBrokenOnesAreReported() throws Exception {
    // Given
    reply = """
        {"rules": [
          {"name": "Users", "method": "ANY", "crud": {"path": "/users", "idType": "number"}},
          {"name": "Relay", "callout": {"mode": "sync", "url": "https://api.example.com/x", "auth": {"type": "bearer", "value": "YOUR_TOKEN"}}},
          {"name": "Ack", "answers": [{"status": 202}], "callout": {"mode": "async", "url": "https://hooks.example.com", "minDelayMs": 1000, "maxDelayMs": 2000}},
          {"name": "Broken", "answers": [{"status": 999}]}
        ]}
        """;
    Map<String, String> headers = Map.of("X-Bin-Token", bin.token(), "Authorization", "Bearer signed-in");

    // When
    JsonNode body = json(draft(headers));

    // Then
    assertThat(body.get("rules")).hasSize(3);
    assertThat(body.at("/rules/0/crud/path").asText()).isEqualTo("/users");
    assertThat(body.at("/rules/1/callout/mode").asText()).isEqualTo("sync");
    assertThat(body.at("/rules/2/callout/maxDelayMs").asInt()).isEqualTo(2000);
    assertThat(body.at("/rule/name").asText()).isEqualTo("Users");
    assertThat(body.at("/skipped/0").asText()).isEqualTo("Broken: Use a status from 100 to 599");
    assertThat(BinDrafter.rules(Jsons.MAPPER.readTree("[" + RULE + "]")).rules()).hasSize(1);
  }

  @Test
  void aBrokenTemplateIsFixedWithAiForSignedInOwnersOnly() throws Exception {
    // Given
    String broken = "{\"template\": \"{\\\"id\\\": \\\"{{jsonPath request.body '$.id'}}\\\"}}\", \"error\": \"found '}}}', expected '}}'\", \"request\": \"POST /orders {\\\"id\\\": 7}\"}";
    Map<String, String> owner = Map.of("X-Bin-Token", bin.token(), "Authorization", "Bearer signed-in");

    // When
    ResponseDefinition fixed = post("/draft/fix", owner, broken);
    ResponseDefinition anonymous = post("/draft/fix", Map.of("X-Bin-Token", bin.token()), broken);
    ResponseDefinition empty = post("/draft/fix", owner, "{\"template\": \" \"}");

    // Then
    assertThat(fixed.getStatus()).isEqualTo(200);
    assertThat(json(fixed).get("template").asText()).isEqualTo("{\"id\": \"{{jsonPath request.body '$.id'}}\" }");
    assertThat(asked.toString()).contains("Error:\nfound '}}}', expected '}}'").contains("A request it answers:\nPOST /orders");
    assertThat(anonymous.getStatus()).isEqualTo(401);
    assertThat(empty.getStatus()).isEqualTo(400);
  }
}
