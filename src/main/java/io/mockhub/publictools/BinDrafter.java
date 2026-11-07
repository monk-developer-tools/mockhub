package io.mockhub.publictools;

import com.fasterxml.jackson.databind.JsonNode;
import io.mockhub.api.Jsons;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

public final class BinDrafter {
  static final int MAX_DESCRIPTION = 1000;
  static final int MAX_RULES = 5;
  static final int MAX_TEMPLATE = BinRule.MAX_BODY;
  static final String SYSTEM = """
      You write rules for a webhook bin. Reply with one JSON object and nothing else: {"rules": [rule, ...]}, one to five
      rules, in the order they should be checked (the first rule that matches answers). Make as many as the request asks for.
      A rule:
      {"name": "short name", "description": "what it is for", "method": "ANY|GET|POST|PUT|PATCH|DELETE|HEAD|OPTIONS",
       "conditions": [{"target": "path|query|header|body|json|form|xml|soap_action|graphql",
                       "key": "name for query/header/form, $.path for json, XPath for xml",
                       "op": "equals|not_equals|starts_with|contains|not_contains|matches|template|exists|absent", "value": "text"}],
       "answers": [{"weight": 1, "status": 200, "delayMs": 0, "headers": {"Content-Type": "application/json"},
                    "body": "text", "template": false, "description": ""}]}
      Paths are relative to the bin, like /payments. "op": "template" matches a path pattern like /users/:id, read as
      {{pathParams.id}}. Use several answers with weights only for random or flaky outcomes. Delays go up to 15000 ms.
      Set "template": true when a body or header uses Handlebars, for example {{jsonPath request.body '$.id'}},
      {{request.path}}, {{request.query.page}}, {{request.headers.X-Id}}, {{now}}, {{randomValue type='UUID'}},
      {{fake 'Name.fullName'}}, and the bin's memory: {{counter 'orders'}}, {{saveValue 'k' 'v'}}, {{savedValue 'k'}},
      {{addToList 'seen' 'x'}}, {{savedList 'seen'}}.
      A rule can call another URL by adding "callout":
      {"mode": "sync|async", "method": "same|GET|POST|PUT|PATCH|DELETE", "url": "https://...", "payload": "original|custom",
       "body": "custom body, may use Handlebars", "headers": {}, "auth": {"type": "none|bearer|basic|header", "name": "", "value": ""},
       "minDelayMs": 0, "maxDelayMs": 0}
      "sync" waits for the URL and answers with its response, so the rule has no "answers". "async" answers with the rule's
      answers at once and calls the URL after a wait between minDelayMs and maxDelayMs, up to 60000.
      A rule can instead be a stateful REST resource with "crud": {"path": "/users", "idField": "id", "idType": "number|uuid|short"},
      method "ANY", no conditions and no answers; it answers list, get, create, replace, patch and delete with stored records.
      Never put real secrets in a rule; use placeholders such as YOUR_TOKEN.
      """;
  static final String FIX = """
      You fix Handlebars templates for a mock HTTP answer (WireMock response templating with MockHub helpers).
      Reply with the corrected template only, inside one ``` block, and nothing else. Keep everything that already works,
      change as little as possible, and keep it valid for the same content type. Helpers you can use include
      {{jsonPath request.body '$.id'}}, {{request.path}}, {{request.query.name}}, {{request.headers.Name}}, {{pathParams.id}},
      {{now}}, {{now offset='1 days' format='yyyy-MM-dd'}}, {{randomValue type='UUID'}}, {{randomInt lower=1 upper=9}},
      {{fake 'Name.fullName'}}, {{#if x}}…{{else}}…{{/if}}, {{#each list}}…{{/each}}, {{#eq a b}}…{{/eq}},
      {{counter 'name'}}, {{saveValue 'k' 'v'}}, {{savedValue 'k' default=''}}, {{addToList 'name' 'v'}}, {{savedList 'name'}}.
      Three closing braces }}} end a triple-stash; put a space between }} and a following } in JSON.
      """;

  public interface Llm {
    String complete(String system, String user);
  }

  static final class Unavailable extends RuntimeException {
    Unavailable(String message) {
      super(message);
    }
  }

  record Drafted(List<BinRule> rules, List<String> skipped) {}

  private final BooleanSupplier enabled;
  private final Llm llm;

  BinDrafter(BooleanSupplier enabled, Llm llm) {
    this.enabled = enabled;
    this.llm = llm;
  }

  boolean available() {
    return enabled.getAsBoolean();
  }

  Drafted draft(String description) {
    if (!available()) throw new Unavailable("This server has no AI provider set up.");
    String text = description == null ? "" : description.strip();
    if (text.isEmpty()) throw new IllegalArgumentException("Describe the rules, for example: answer 402 when the event is payment.failed");
    if (text.length() > MAX_DESCRIPTION) throw new IllegalArgumentException("Keep the description under " + MAX_DESCRIPTION + " characters");
    return rules(parse(llm.complete(SYSTEM, text)));
  }

  String fix(String template, String error, String sample) {
    if (!available()) throw new Unavailable("This server has no AI provider set up.");
    if (template == null || template.isBlank()) throw new IllegalArgumentException("Send the template to fix");
    if (template.length() > MAX_TEMPLATE) throw new IllegalArgumentException("Keep the template under 16 KB");
    String why = error == null ? "" : error.strip();
    String user = "Template:\n" + template + "\n\nError:\n" + (why.isEmpty() ? "(none given; it doesn't do what was meant)" : cut(why, 2000))
        + (sample == null || sample.isBlank() ? "" : "\n\nA request it answers:\n" + cut(sample, 4000));
    String reply = llm.complete(FIX, user);
    String fixed = LlmJson.unfence(reply);
    if (fixed.isBlank()) throw new IllegalArgumentException("The AI didn't return a template. Try again.");
    if (fixed.length() > MAX_TEMPLATE) throw new IllegalArgumentException("The AI's template was over 16 KB");
    return fixed;
  }

  static Drafted rules(JsonNode node) {
    JsonNode list = node.isArray() ? node : node.has("rules") ? node.get("rules") : Jsons.MAPPER.createArrayNode().add(node);
    if (!list.isArray() || list.isEmpty()) throw new IllegalArgumentException("The AI didn't return a rule. Try describing it differently.");
    List<BinRule> out = new ArrayList<>();
    List<String> skipped = new ArrayList<>();
    for (JsonNode n : list) {
      if (out.size() >= MAX_RULES) break;
      try {
        out.add(BinRule.from(n));
      } catch (IllegalArgumentException e) {
        skipped.add(n.path("name").asText("A rule") + ": " + e.getMessage());
      }
    }
    if (out.isEmpty()) throw new IllegalArgumentException("The AI's rule didn't work: " + skipped.get(0) + ". Try describing it differently.");
    return new Drafted(List.copyOf(out), List.copyOf(skipped));
  }

  static JsonNode parse(String reply) {
    return LlmJson.parse(reply, "The AI didn't return a rule. Try describing it differently.");
  }

  private static String cut(String text, int max) {
    return text.length() <= max ? text : text.substring(0, max);
  }
}