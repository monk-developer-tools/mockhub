package io.mockhub.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.mockhub.api.Jsons;
import io.mockhub.support.FakeLlmTransport;
import org.junit.jupiter.api.Test;

class ChatCompletionsTest {

  private final FakeLlmTransport transport = new FakeLlmTransport();
  private final ChatCompletions chat = new ChatCompletions(transport, AiProviders.defaults(), "https://mocks.acme.dev");

  private static AiSettings settings(String provider, String baseUrl, String key, String model) {
    return new AiSettings(true, provider, baseUrl, key, model, 0.2, 8);
  }

  @Test
  void callsOpenRouterWithItsDefaultsAndAttributionHeaders() {
    // Given
    transport.reply("{'choices':[{'message':{'role':'assistant','content':'hi'},'finish_reason':'stop'}],'usage':{'prompt_tokens':12,'completion_tokens':3}}");

    // When
    ChatCompletions.Completion result = chat.complete(settings("openrouter", "", "sk-or-1", ""), Jsons.MAPPER.createArrayNode(), null);

    // Then
    FakeLlmTransport.Call call = transport.calls.get(0);
    assertThat(call.url()).isEqualTo("https://openrouter.ai/api/v1/chat/completions");
    assertThat(call.headers())
        .containsEntry("Authorization", "Bearer sk-or-1")
        .containsEntry("HTTP-Referer", "https://mocks.acme.dev")
        .containsEntry("X-Title", "MockHub");
    assertThat(call.body().get("model").asText()).isEqualTo("openrouter/auto");
    assertThat(call.body().has("tools")).isFalse();
    assertThat(result.message().get("content").asText()).isEqualTo("hi");
    assertThat(result.promptTokens()).isEqualTo(12);
  }

  @Test
  void callsALiteLlmProxyWithoutAKeyAndSendsTools() {
    // Given
    transport.reply("{'choices':[{'message':{'content':'ok'}}]}");

    // When
    chat.complete(settings("litellm", "http://litellm:4000/", "", "gpt-4o"), Jsons.MAPPER.createArrayNode(), MockTools.definitions());

    // Then
    FakeLlmTransport.Call call = transport.calls.get(0);
    assertThat(call.url()).isEqualTo("http://litellm:4000/chat/completions");
    assertThat(call.headers()).doesNotContainKey("Authorization");
    assertThat(call.body().get("tool_choice").asText()).isEqualTo("auto");
    assertThat(call.body().get("tools")).hasSize(6);
  }

  @Test
  void explainsMissingKeysModelsAndEmptyAnswers() {
    // Then
    assertThatThrownBy(() -> chat.complete(settings("openrouter", "", "", ""), Jsons.MAPPER.createArrayNode(), null))
        .hasMessageContaining("needs an API key");
    assertThatThrownBy(() -> chat.complete(settings("litellm", "", "", ""), Jsons.MAPPER.createArrayNode(), null))
        .hasMessageContaining("Choose a model");
    transport.reply("{'error':{'message':'model not found'}}");
    assertThatThrownBy(() -> chat.complete(settings("litellm", "", "", "x"), Jsons.MAPPER.createArrayNode(), null))
        .hasMessageContaining("model not found");
  }

  @Test
  void listsModels() {
    // Given
    transport.models("{'data':[{'id':'anthropic/claude'},{'id':'openai/gpt'}]}");

    // Then
    assertThat(chat.models(settings("openrouter", "", "k", ""))).containsExactly("anthropic/claude", "openai/gpt");
    assertThat(transport.calls.get(0).url()).isEqualTo("https://openrouter.ai/api/v1/models");
  }
}
