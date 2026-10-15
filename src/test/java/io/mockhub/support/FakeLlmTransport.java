package io.mockhub.support;

import com.fasterxml.jackson.databind.JsonNode;
import io.mockhub.ai.AiException;
import io.mockhub.ai.LlmTransport;
import io.mockhub.api.Jsons;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;

public final class FakeLlmTransport implements LlmTransport {
  public record Call(String url, Map<String, String> headers, JsonNode body) {}

  public final List<Call> calls = new ArrayList<>();
  private final Deque<JsonNode> replies = new ArrayDeque<>();
  private JsonNode models;

  public FakeLlmTransport reply(String json) {
    replies.add(Fixtures.tree(json));
    return this;
  }

  public FakeLlmTransport reply(JsonNode json) {
    replies.add(json);
    return this;
  }

  public static JsonNode toolCall(String id, String name, JsonNode arguments) {
    var fn = Jsons.object().put("name", name).put("arguments", Jsons.write(arguments));
    var call = Jsons.object().put("id", id).put("type", "function");
    call.set("function", fn);
    var message = Jsons.object().put("role", "assistant");
    message.putNull("content");
    message.putArray("tool_calls").add(call);
    var response = Jsons.object();
    response.putArray("choices").addObject().set("message", message);
    response.putObject("usage").put("prompt_tokens", 100).put("completion_tokens", 20);
    return response;
  }

  public FakeLlmTransport models(String json) {
    models = Fixtures.tree(json);
    return this;
  }

  @Override
  public JsonNode get(String url, Map<String, String> headers) {
    calls.add(new Call(url, headers, null));
    if (models == null) throw new AiException("no models");
    return models;
  }

  @Override
  public JsonNode post(String url, Map<String, String> headers, JsonNode body) {
    calls.add(new Call(url, headers, body.deepCopy()));
    if (replies.isEmpty()) throw new AiException("No scripted reply left");
    return replies.poll();
  }
}
