package io.mockhub.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import io.mockhub.api.ApiException;
import io.mockhub.api.Jsons;
import io.mockhub.support.FakeLlmTransport;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BodyGeneratorTest {

  @TempDir
  Path dir;
  private final FakeLlmTransport transport = new FakeLlmTransport();
  private AiSettingsStore settings;
  private BodyGenerator generator;

  @BeforeEach
  void setUp() {
    settings = new AiSettingsStore(dir, "");
    settings.save(new AiSettings(true, "litellm", "http://llm", "", "gpt-test", 0.2, 3));
    generator = new BodyGenerator(settings, new ChatCompletions(transport, AiProviders.defaults(), ""));
  }

  private static JsonNode reply(String content) {
    var message = Jsons.object().put("role", "assistant").put("content", content);
    var response = Jsons.object();
    response.putArray("choices").addObject().set("message", message);
    return response;
  }

  @Test
  void returnsPrettyJsonWithoutCodeFences() {
    // Given
    transport.reply(reply("Here you go:\n```json\n{\"id\":\"ord_1\",\"total\":12.5}\n```"));

    // When
    String body = generator.generate(new BodyGenerator.Request("an order", "json", "GET", "/shop/orders/1", 200, ""));

    // Then
    assertThat(body).isEqualTo("{\n  \"id\" : \"ord_1\",\n  \"total\" : 12.5\n}");
    String prompt = transport.calls.get(0).body().at("/messages/1/content").asText();
    assertThat(prompt).contains("GET /shop/orders/1").contains("Status: 200").contains("an order");
  }

  @Test
  void extractsJsonSurroundedByText() {
    // When
    String body = BodyGenerator.prettyJson("Sure! [1, 2] hope this helps");

    // Then
    assertThat(body).isEqualTo("[ 1, 2 ]");
  }

  @Test
  void keepsXmlAsWritten() {
    // Given
    transport.reply(reply("<a><b>1</b></a>"));

    // When
    String body = generator.generate(new BodyGenerator.Request("xml please", "xml", null, null, 0, null));

    // Then
    assertThat(body).isEqualTo("<a><b>1</b></a>");
  }

  @Test
  void failsWhenTheReplyIsNotJson() {
    // Given
    transport.reply(reply("no idea"));

    // When / Then
    assertThatThrownBy(() -> generator.generate(new BodyGenerator.Request("x", "json", "GET", "/a", 200, "")))
        .isInstanceOf(AiException.class);
  }

  @Test
  void refusesWhenTheAssistantIsOffOrTheDescriptionIsEmpty() {
    // Given
    BodyGenerator.Request empty = new BodyGenerator.Request(" ", "json", "GET", "/a", 200, "");

    // When / Then
    assertThatThrownBy(() -> generator.generate(empty)).isInstanceOf(ApiException.class);
    settings.save(new AiSettings(false, "litellm", "http://llm", "", "gpt-test", 0.2, 3));
    assertThatThrownBy(() -> generator.generate(new BodyGenerator.Request("x", "json", "GET", "/a", 200, "")))
        .isInstanceOf(ApiException.class).hasMessageContaining("turned off");
  }
}
