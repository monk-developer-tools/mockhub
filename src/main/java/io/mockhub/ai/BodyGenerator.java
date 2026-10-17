package io.mockhub.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import io.mockhub.api.ApiException;
import io.mockhub.api.Jsons;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class BodyGenerator {
  public static final int MAX_DESCRIPTION = 2000;
  private static final int MAX_CURRENT = 8000;
  private static final Pattern FENCE = Pattern.compile("```[a-zA-Z0-9+-]*\\s*\\n?(.*?)```", Pattern.DOTALL);

  private final AiSettingsStore settings;
  private final ChatCompletions chat;

  public BodyGenerator(AiSettingsStore settings, ChatCompletions chat) {
    this.settings = settings;
    this.chat = chat;
  }

  public record Request(String description, String format, String method, String path, int status, String current) {}

  public String generate(Request request) {
    AiSettings s = settings.get();
    if (!s.enabled()) throw new ApiException(409, "The AI assistant is turned off. An admin can turn it on under AI assistant.");
    String description = request.description() == null ? "" : request.description().trim();
    if (description.isEmpty()) throw ApiException.badRequest("Describe the response you want");
    if (description.length() > MAX_DESCRIPTION) throw ApiException.badRequest("Keep the description under " + MAX_DESCRIPTION + " characters");
    String format = normalise(request.format());
    ArrayNode messages = Jsons.MAPPER.createArrayNode();
    messages.addObject().put("role", "system").put("content", systemPrompt(format));
    messages.addObject().put("role", "user").put("content", userPrompt(request, description));
    ChatCompletions.Completion completion = chat.complete(s, messages, null);
    String text = clean(completion.message().path("content").asText(""));
    if (text.isEmpty()) throw new AiException("The AI provider returned an empty body");
    return format.equals("json") ? prettyJson(text) : text;
  }

  static String clean(String content) {
    String text = content.strip();
    Matcher fence = FENCE.matcher(text);
    if (fence.find()) text = fence.group(1).strip();
    return text;
  }

  static String prettyJson(String text) {
    try {
      return Jsons.MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(Jsons.MAPPER.readTree(text));
    } catch (Exception e) {
      int start = firstOf(text, '{', '[');
      int end = Math.max(text.lastIndexOf('}'), text.lastIndexOf(']'));
      if (start >= 0 && end > start) {
        try {
          JsonNode node = Jsons.MAPPER.readTree(text.substring(start, end + 1));
          return Jsons.MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(node);
        } catch (Exception ignored) {
          throw new AiException("The AI provider didn't return valid JSON. Try again or describe it differently.");
        }
      }
      throw new AiException("The AI provider didn't return valid JSON. Try again or describe it differently.");
    }
  }

  private static int firstOf(String text, char a, char b) {
    int i = text.indexOf(a);
    int j = text.indexOf(b);
    if (i < 0) return j;
    if (j < 0) return i;
    return Math.min(i, j);
  }

  private static String normalise(String format) {
    return switch (format == null ? "" : format) {
      case "xml" -> "xml";
      case "text" -> "text";
      default -> "json";
    };
  }

  private static String systemPrompt(String format) {
    String what = switch (format) {
      case "xml" -> "an XML document (for SOAP, a full soap:Envelope)";
      case "text" -> "plain text";
      default -> "a single JSON value";
    };
    return "You write response bodies for mock APIs used in software testing. Reply with " + what
        + " and nothing else: no explanation, no markdown, no code fences. Use realistic, varied values "
        + "(names, ids, dates in ISO 8601, amounts with currency), never placeholders like \"string\" or \"foo\". "
        + "Keep lists to 3 items unless asked otherwise. Never include real people's personal data or secrets.";
  }

  private static String userPrompt(Request r, String description) {
    StringBuilder b = new StringBuilder();
    if (r.method() != null && r.path() != null && !r.path().isBlank()) {
      b.append("Endpoint: ").append(r.method()).append(' ').append(r.path()).append('\n');
    }
    if (r.status() > 0) b.append("Status: ").append(r.status()).append('\n');
    if (r.current() != null && !r.current().isBlank()) {
      String current = r.current().length() > MAX_CURRENT ? r.current().substring(0, MAX_CURRENT) : r.current();
      b.append("Current body, to change or build on:\n").append(current).append('\n');
    }
    return b.append("Write the body: ").append(description).toString();
  }
}
