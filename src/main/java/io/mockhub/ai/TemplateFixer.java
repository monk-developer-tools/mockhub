package io.mockhub.ai;

import java.util.function.BinaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TemplateFixer {
  public static final int MAX_PROJECT_TEMPLATE = 64 * 1024;
  public static final String BIN_HELPERS = "{{counter 'name'}}, {{saveValue 'k' 'v'}}, {{savedValue 'k' default=''}}, {{addToList 'name' 'v'}}, {{savedList 'name'}}";
  public static final String PROJECT_HELPERS = "{{state context=(jsonPath request.body '$.id') property='status' default='NEW'}}, "
      + "{{{dataset 'customers' city=request.query.city limit=20}}}, {{#with (dataset-one 'customers' id=request.path.id)}}{{name}}{{/with}}";
  private static final Pattern FENCE = Pattern.compile("```[a-zA-Z0-9+-]*[ \\t]*\\r?\\n?(.*?)```", Pattern.DOTALL);
  private static final String SYSTEM = """
      You fix Handlebars templates for a mock HTTP answer (WireMock response templating with MockHub helpers).
      Reply with the corrected template only, inside one ``` block, and nothing else. Keep everything that already works,
      change as little as possible, and keep it valid for the same content type. Helpers you can use include
      {{jsonPath request.body '$.id'}}, {{request.path}}, {{request.query.name}}, {{request.headers.Name}}, {{pathParams.id}},
      {{now}}, {{now offset='1 days' format='yyyy-MM-dd'}}, {{randomValue type='UUID'}}, {{randomInt lower=1 upper=9}},
      {{fake 'Name.fullName'}}, {{#if x}}…{{else}}…{{/if}}, {{#each list}}…{{/each}}, {{#eq a b}}…{{/eq}},
      {{#switch x}}{{#case 'a'}}…{{/case}}{{#default}}…{{/default}}{{/switch}}, {{#repeat 3}}…{{/repeat}},
      {{dateAdd date '{days:3}' 'iso'}}, {{coalesce a b 'fallback'}}, {{default x 'fallback'}},
      """;
  private static final String RULES = """
      .
      Three closing braces }}} end a triple-stash; put a space between }} and a following } in JSON.
      """;

  private final BinaryOperator<String> complete;
  private final String helpers;
  private final int maxTemplate;

  public TemplateFixer(BinaryOperator<String> complete, String helpers, int maxTemplate) {
    this.complete = complete;
    this.helpers = helpers;
    this.maxTemplate = maxTemplate;
  }

  static String system(String helpers) {
    return SYSTEM + helpers + RULES;
  }

  public String fix(String template, String error, String sample) {
    if (template == null || template.isBlank()) throw new IllegalArgumentException("Send the template to fix");
    if (template.length() > maxTemplate) throw new IllegalArgumentException("Keep the template under " + size(maxTemplate));
    String why = error == null ? "" : error.strip();
    String user = "Template:\n" + template + "\n\nError:\n" + (why.isEmpty() ? "(none given; it doesn't do what was meant)" : cut(why, 2000))
        + (sample == null || sample.isBlank() ? "" : "\n\nA request it answers:\n" + cut(sample, 4000));
    String fixed = unfence(complete.apply(system(helpers), user));
    if (fixed.isBlank()) throw new IllegalArgumentException("The AI didn't return a template. Try again.");
    if (fixed.length() > maxTemplate) throw new IllegalArgumentException("The AI's template was over " + size(maxTemplate));
    return fixed;
  }

  static String unfence(String reply) {
    String text = reply == null ? "" : reply.strip();
    Matcher fence = FENCE.matcher(text);
    return fence.find() ? fence.group(1).replaceAll("\\s+$", "") : text;
  }

  private static String size(int bytes) {
    return bytes / 1024 + " KB";
  }

  private static String cut(String text, int max) {
    return text.length() <= max ? text : text.substring(0, max) + "…";
  }
}
