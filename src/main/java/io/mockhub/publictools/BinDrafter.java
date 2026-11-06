package io.mockhub.publictools;

import com.fasterxml.jackson.databind.JsonNode;
import io.mockhub.api.Jsons;
import java.util.function.BooleanSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class BinDrafter {
  static final int MAX_DESCRIPTION = 1000;
  private static final Pattern FENCE = Pattern.compile("```[a-zA-Z0-9+-]*\\s*\\n?(.*?)```", Pattern.DOTALL);
  static final String SYSTEM = """
      You write one rule for a webhook bin. Reply with a single JSON object and nothing else, in this shape:
      {"name": "short name", "method": "ANY|GET|POST|PUT|PATCH|DELETE",
       "conditions": [{"target": "path|query|header|body|json", "key": "name for query/header, $.path for json",
                       "op": "equals|starts_with|contains|matches|exists", "value": "text"}],
       "answers": [{"weight": 1, "status": 200, "delayMs": 0, "headers": {"Content-Type": "application/json"},
                    "body": "text", "template": false}]}
      Paths are relative to the bin, like /payments. Use more than one answer only for weighted or random outcomes.
      Set "template": true when the body uses Handlebars helpers, for example {{jsonPath request.body '$.id'}},
      {{request.path}}, {{now}}, {{randomValue type='UUID'}}, {{fake 'Name.fullName'}}. Delays go up to 15000 ms.
      """;

  public interface Llm {
    String complete(String system, String user);
  }

  static final class Unavailable extends RuntimeException {
    Unavailable(String message) {
      super(message);
    }
  }

  private final BooleanSupplier enabled;
  private final Llm llm;

  BinDrafter(BooleanSupplier enabled, Llm llm) {
    this.enabled = enabled;
    this.llm = llm;
  }

  boolean available() {
    return enabled.getAsBoolean();
  }

  BinRule draft(String description) {
    if (!available()) throw new Unavailable("Describing a rule isn't turned on for this server.");
    String text = description == null ? "" : description.strip();
    if (text.isEmpty()) throw new IllegalArgumentException("Describe the rule, for example: answer 402 when the event is payment.failed");
    if (text.length() > MAX_DESCRIPTION) throw new IllegalArgumentException("Keep the description under " + MAX_DESCRIPTION + " characters");
    String reply = llm.complete(SYSTEM, text);
    return BinRule.from(parse(reply));
  }

  static JsonNode parse(String reply) {
    String text = reply == null ? "" : reply.strip();
    Matcher fence = FENCE.matcher(text);
    if (fence.find()) text = fence.group(1).strip();
    int start = text.indexOf('{');
    int end = text.lastIndexOf('}');
    if (start < 0 || end <= start) throw new IllegalArgumentException("The AI didn't return a rule. Try describing it differently.");
    try {
      return Jsons.MAPPER.readTree(text.substring(start, end + 1));
    } catch (Exception e) {
      throw new IllegalArgumentException("The AI didn't return a rule. Try describing it differently.");
    }
  }
}
