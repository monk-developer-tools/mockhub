package io.mockhub.publictools;

import com.fasterxml.jackson.databind.JsonNode;
import io.mockhub.api.Jsons;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class LlmJson {
  private static final Pattern FENCE = Pattern.compile("```[a-zA-Z0-9+-]*[ \\t]*\\r?\\n?(.*?)```", Pattern.DOTALL);

  private LlmJson() {}

  static JsonNode parse(String reply, String failure) {
    String text = unfence(reply);
    int object = text.indexOf('{');
    int array = text.indexOf('[');
    int start = array >= 0 && (object < 0 || array < object) ? array : object;
    int end = Math.max(text.lastIndexOf('}'), text.lastIndexOf(']'));
    if (start < 0 || end <= start) throw new IllegalArgumentException(failure);
    try {
      return Jsons.MAPPER.readTree(text.substring(start, end + 1));
    } catch (Exception e) {
      throw new IllegalArgumentException(failure);
    }
  }

  static String unfence(String reply) {
    String text = reply == null ? "" : reply.strip();
    Matcher fence = FENCE.matcher(text);
    return fence.find() ? fence.group(1).replaceAll("\\s+$", "") : text;
  }
}
