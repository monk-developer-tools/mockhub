package io.mockhub.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import io.mockhub.ai.AiException;
import io.mockhub.ai.AiProviders;
import io.mockhub.ai.AiSettings;
import io.mockhub.ai.AiSettingsStore;
import io.mockhub.ai.ChatCompletions;
import io.mockhub.api.Jsons;
import io.mockhub.history.HistoryEntry;
import io.mockhub.support.FakeLlmTransport;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ContractPatcherTest {

  @TempDir
  Path dir;
  private final FakeLlmTransport transport = new FakeLlmTransport();
  private ContractPatcher patcher;
  private Contract contract;
  private final DriftState.Finding refunds = new DriftState.Finding("k1", DriftKind.UNDOCUMENTED_PATH, "GET", "/refunds/{id}", "", 4,
      "2026-11-05T10:00:00Z", "2026-11-05T10:05:00Z", "e9", "/orders/refunds/3", 200);

  @BeforeEach
  void setUp() {
    AiSettingsStore settings = new AiSettingsStore(dir, "");
    settings.save(new AiSettings(true, "litellm", "http://llm", "", "gpt-test", 0.2, 3));
    HistoryEntry example = new HistoryEntry("e9", 1L, "p1", "GET", "/orders/refunds/3", "", "", List.of(), "", false, 200, List.of(),
        "{\"id\":3,\"amount\":9.5}", false, true, "s", "n", 1);
    patcher = new ContractPatcher(settings, new ChatCompletions(transport, AiProviders.defaults(), ""),
        (project, id) -> id.equals("e9") ? Optional.of(example) : Optional.empty());
    contract = new Contract(ContractDocument.parse(ContractCheckerTest.ORDERS), "upload", null, "t", null, null);
  }

  private static JsonNode reply(String content) {
    var response = Jsons.object();
    response.putArray("choices").addObject().putObject("message").put("role", "assistant").put("content", content);
    return response;
  }

  @Test
  void appliesTheSuggestedPatchToACopyOfTheContract() {
    // Given
    transport.reply(reply("```json\n{\"summary\":\"Documents refunds\",\"patch\":[{\"op\":\"add\",\"path\":\"/paths/~1refunds~1{id}\","
        + "\"value\":{\"get\":{\"responses\":{\"200\":{\"description\":\"ok\"}}}}}]}\n```"));

    // When
    ContractPatcher.Suggestion s = patcher.suggest("p1", "/orders", contract, List.of(refunds));

    // Then
    assertThat(s.summary()).isEqualTo("Documents refunds");
    assertThat(s.spec().path("paths").has("/refunds/{id}")).isTrue();
    assertThat(contract.spec().path("paths").has("/refunds/{id}")).isFalse();
    String prompt = transport.calls.get(0).body().at("/messages/1/content").asText();
    assertThat(prompt).contains("Path not in the contract: GET /refunds/{id}").contains("Example: GET /refunds/3")
        .contains("{\"id\":3,\"amount\":9.5}").contains("Documented paths: /orders, /orders/{id}, /orders/latest");
  }

  @Test
  void includesTheTouchedPathsAndTheirSchemas() {
    // Given
    DriftState.Finding schema = new DriftState.Finding("k2", DriftKind.RESPONSE_BODY_SCHEMA, "GET", "/orders/{id}", "200 $.amount: must have a minimum value of 0",
        1, "t", "t", null, "/orders/orders/1", 200);
    transport.reply(reply("[{\"op\":\"remove\",\"path\":\"/components/schemas/Order/properties/amount/minimum\"}]"));

    // When
    ContractPatcher.Suggestion s = patcher.suggest("p1", "/orders", contract, List.of(schema));

    // Then
    assertThat(s.spec().at("/components/schemas/Order/properties/amount").has("minimum")).isFalse();
    String prompt = transport.calls.get(0).body().at("/messages/1/content").asText();
    assertThat(prompt).contains("paths[\"/orders/{id}\"]").contains("schema Order = ");
  }

  @Test
  void aPatchThatDoesNotFitIsRefused() {
    // Given
    transport.reply(reply("{\"patch\":[{\"op\":\"replace\",\"path\":\"/paths/~1nope\",\"value\":{}}]}"));

    // When / Then
    assertThatThrownBy(() -> patcher.suggest("p1", "/orders", contract, List.of(refunds)))
        .isInstanceOf(AiException.class).hasMessageContaining("doesn't fit the contract");
  }

  @Test
  void anAnswerThatIsNotJsonIsRefused() {
    // Given
    transport.reply(reply("I think you should add a path."));

    // When / Then
    assertThatThrownBy(() -> patcher.suggest("p1", "/orders", contract, List.of(refunds))).hasMessageContaining("didn't return a JSON patch");
  }
}
